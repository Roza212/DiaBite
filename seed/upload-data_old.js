const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Configuration
const CONFIG = {
  BATCH_SIZE: 500, // Firestore batch limit is 500
  RETRY_ATTEMPTS: 3,
  RETRY_DELAY: 1000, // 1 second
  COLLECTIONS: ['foodItems', 'categories', 'conditions']
};

// Initialize Firebase Admin
let db;
let serviceAccount;

try {
  serviceAccount = require('./serviceAccountKey.json');
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
  db = admin.firestore();
} catch (error) {
  console.error('❌ Failed to initialize Firebase Admin:', error.message);
  console.error('Make sure serviceAccountKey.json exists and is valid');
  process.exit(1);
}

/**
 * Custom error class for upload operations
 */
class UploadError extends Error {
  constructor(message, code, originalError = null) {
    super(message);
    this.name = 'UploadError';
    this.code = code;
    this.originalError = originalError;
  }
}

/**
 * Sleep utility for retry delays
 */
const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Validate JSON data structure
 */
function validateData(data) {
  if (!data || typeof data !== 'object') {
    throw new UploadError('Invalid data format: expected object', 'INVALID_DATA');
  }

  CONFIG.COLLECTIONS.forEach(collection => {
    if (!Array.isArray(data[collection])) {
      throw new UploadError(`Invalid data format: ${collection} should be an array`, 'INVALID_DATA');
    }

    data[collection].forEach((item, index) => {
      if (!item.id) {
        throw new UploadError(`Missing id in ${collection} item at index ${index}`, 'MISSING_ID');
      }
      if (!item.name && collection !== 'conditions') {
        throw new UploadError(`Missing name in ${collection} item at index ${index}`, 'MISSING_NAME');
      }
    });
  });

  return true;
}

/**
 * Read and parse JSON file with error handling
 */
function readJsonFile(filePath) {
  try {
    if (!fs.existsSync(filePath)) {
      throw new UploadError(`File not found: ${filePath}`, 'FILE_NOT_FOUND');
    }

    const fileContent = fs.readFileSync(filePath, 'utf8');
    const data = JSON.parse(fileContent);

    validateData(data);
    return data;
  } catch (error) {
    if (error instanceof UploadError) throw error;
    if (error instanceof SyntaxError) {
      throw new UploadError(`Invalid JSON format: ${error.message}`, 'INVALID_JSON', error);
    }
    throw new UploadError(`Failed to read file: ${error.message}`, 'FILE_READ_ERROR', error);
  }
}

/**
 * Check if document exists
 */
async function documentExists(collection, docId) {
  try {
    const doc = await db.collection(collection).doc(docId).get();
    return doc.exists;
  } catch (error) {
    throw new UploadError(`Failed to check document existence: ${error.message}`, 'FIRESTORE_ERROR', error);
  }
}

/**
 * Add metadata to document
 */
function addMetadata(doc, source = 'curated') {
  const now = new Date().toISOString();
  return {
    ...doc,
    dataSource: source,
    createdAt: doc.createdAt || now,
    lastUpdated: now,
    uploadedAt: now,
    uploadedBy: 'system_upload_script',
    version: 1
  };
}

/**
 * Upload documents in batches with retry logic
 */
async function uploadBatch(collection, documents, batchIndex = 0) {
  const batch = db.batch();
  const batchDocs = documents.slice(batchIndex * CONFIG.BATCH_SIZE, (batchIndex + 1) * CONFIG.BATCH_SIZE);

  if (batchDocs.length === 0) return { success: true, uploaded: 0 };

  batchDocs.forEach(doc => {
    const docRef = db.collection(collection).doc(doc.id);
    const docWithMetadata = addMetadata(doc);
    batch.set(docRef, docWithMetadata, { merge: true }); // Use merge to avoid overwriting
  });

  for (let attempt = 1; attempt <= CONFIG.RETRY_ATTEMPTS; attempt++) {
    try {
      await batch.commit();
      return { success: true, uploaded: batchDocs.length };
    } catch (error) {
      console.warn(`⚠️  Batch upload attempt ${attempt} failed: ${error.message}`);

      if (attempt === CONFIG.RETRY_ATTEMPTS) {
        throw new UploadError(
          `Failed to upload batch after ${CONFIG.RETRY_ATTEMPTS} attempts: ${error.message}`,
          'BATCH_UPLOAD_FAILED',
          error
        );
      }

      await sleep(CONFIG.RETRY_DELAY * attempt);
    }
  }
}

/**
 * Upload collection with progress tracking
 */
async function uploadCollection(collection, items, options = {}) {
  const { skipDuplicates = true, dryRun = false } = options;

  console.log(`\n📁 Processing collection: ${collection}`);
  console.log(`   Items to process: ${items.length}`);

  if (dryRun) {
    console.log(`   🔍 Dry run mode - no actual upload`);
    return { processed: items.length, uploaded: 0, skipped: 0, errors: 0 };
  }

  let processed = 0;
  let uploaded = 0;
  let skipped = 0;
  let errors = 0;
  const errorDetails = [];

  // Process in batches
  for (let i = 0; i < items.length; i += CONFIG.BATCH_SIZE) {
    const batchItems = items.slice(i, i + CONFIG.BATCH_SIZE);
    const batchNumber = Math.floor(i / CONFIG.BATCH_SIZE) + 1;
    const totalBatches = Math.ceil(items.length / CONFIG.BATCH_SIZE);

    console.log(`   📦 Processing batch ${batchNumber}/${totalBatches} (${batchItems.length} items)`);

    // Check for duplicates if requested
    if (skipDuplicates) {
      const duplicateChecks = await Promise.allSettled(
        batchItems.map(item => documentExists(collection, item.id))
      );

      const filteredItems = batchItems.filter((item, index) => {
        const check = duplicateChecks[index];
        if (check.status === 'fulfilled' && check.value) {
          console.log(`      ⏭️  Skipped duplicate: ${item.name || item.id}`);
          skipped++;
          return false;
        }
        return true;
      });

      if (filteredItems.length === 0) {
        console.log(`      ✅ Batch ${batchNumber} completed (all duplicates)`);
        continue;
      }

      batchItems = filteredItems;
    }

    try {
      const result = await uploadBatch(collection, batchItems, 0);
      uploaded += result.uploaded;
      console.log(`      ✅ Batch ${batchNumber} uploaded successfully (${result.uploaded} items)`);
    } catch (error) {
      errors += batchItems.length;
      errorDetails.push({
        batch: batchNumber,
        items: batchItems.map(item => item.id),
        error: error.message
      });
      console.error(`      ❌ Batch ${batchNumber} failed: ${error.message}`);
    }

    processed += batchItems.length;
  }

  console.log(`   📊 ${collection} summary: ${processed} processed, ${uploaded} uploaded, ${skipped} skipped, ${errors} errors`);

  return { processed, uploaded, skipped, errors, errorDetails };
}

/**
 * Generate upload report
 */
function generateReport(results) {
  const totalProcessed = results.reduce((sum, r) => sum + r.processed, 0);
  const totalUploaded = results.reduce((sum, r) => sum + r.uploaded, 0);
  const totalSkipped = results.reduce((sum, r) => sum + r.skipped, 0);
  const totalErrors = results.reduce((sum, r) => sum + r.errors, 0);

  return {
    timestamp: new Date().toISOString(),
    totalProcessed,
    totalUploaded,
    totalSkipped,
    totalErrors,
    collections: results,
    success: totalErrors === 0
  };
}

/**
 * Main upload function
 */
async function uploadFoodData(jsonFilePath, options = {}) {
  const {
    skipDuplicates = true,
    dryRun = false,
    collections = CONFIG.COLLECTIONS
  } = options;

  console.log('🚀 Starting food data upload process...');
  console.log(`   File: ${jsonFilePath}`);
  console.log(`   Skip duplicates: ${skipDuplicates}`);
  console.log(`   Dry run: ${dryRun}`);
  console.log(`   Collections: ${collections.join(', ')}`);

  try {
    // Read and validate data
    console.log('\n📖 Reading and validating data...');
    const data = readJsonFile(jsonFilePath);
    console.log('✅ Data validation successful');

    // Process each collection
    const results = [];
    for (const collection of collections) {
      if (!data[collection]) {
        console.warn(`⚠️  Collection '${collection}' not found in data, skipping`);
        continue;
      }

      const result = await uploadCollection(collection, data[collection], { skipDuplicates, dryRun });
      results.push({ collection, ...result });
    }

    // Generate and display report
    const report = generateReport(results);

    console.log('\n📊 UPLOAD REPORT');
    console.log('='.repeat(50));
    console.log(`Total processed: ${report.totalProcessed}`);
    console.log(`Total uploaded: ${report.totalUploaded}`);
    console.log(`Total skipped: ${report.totalSkipped}`);
    console.log(`Total errors: ${report.totalErrors}`);
    console.log(`Success: ${report.success ? '✅' : '❌'}`);

    if (report.totalErrors > 0) {
      console.log('\n❌ ERRORS:');
      results.forEach(result => {
        if (result.errorDetails && result.errorDetails.length > 0) {
          console.log(`   ${result.collection}:`);
          result.errorDetails.forEach(error => {
            console.log(`     Batch ${error.batch}: ${error.error}`);
          });
        }
      });
    }

    console.log('\n' + (report.success ? '🎉 Upload completed successfully!' : '⚠️  Upload completed with errors'));

    return report;

  } catch (error) {
    console.error('\n💥 CRITICAL ERROR:', error.message);
    if (error.code) console.error(`Error code: ${error.code}`);
    if (error.originalError) console.error(`Original error:`, error.originalError);

    throw error;
  }
}

/**
 * CLI interface
 */
async function main() {
  const args = process.argv.slice(2);
  const jsonFile = args[0] || './food-data.json';

  const options = {
    skipDuplicates: !args.includes('--no-skip-duplicates'),
    dryRun: args.includes('--dry-run')
  };

  try {
    const report = await uploadFoodData(jsonFile, options);

    if (!report.success && !options.dryRun) {
      console.log('\n⚠️  Upload completed with errors. Check the output above for details.');
      process.exit(1);
    }

    process.exit(0);
  } catch (error) {
    console.error('\n💥 Fatal error:', error.message);
    process.exit(1);
  }
}

// Export for testing
module.exports = {
  uploadFoodData,
  validateData,
  UploadError
};

// Run if called directly
if (require.main === module) {
  main();
}
