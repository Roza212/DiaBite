package com.example.diabite.data.repository

import com.example.diabite.data.model.User
import com.example.diabite.data.model.UserFavorite
import com.example.diabite.data.model.UserHistory
import com.example.diabite.domain.repository.AuthRepository
import com.example.diabite.util.AppError
import com.example.diabite.util.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override fun signUp(email: String, password: String, user: User): Flow<Resource<User>> = flow {
        emit(Resource.loading())

        try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                val newUser = user.copy(
                    uid = firebaseUser.uid,
                    email = email
                )
                // Save user to Firestore
                firestore.collection("users").document(firebaseUser.uid).set(newUser).await()
                emit(Resource.success(newUser))
            } else {
                emit(Resource.error(AppError.UnknownError("Registration failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "Sign up failed")
            emit(Resource.firebaseError(e))
        }
    }

    override fun login(email: String, password: String): Flow<Resource<User>> = flow {
        emit(Resource.loading())

        try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user
            if (firebaseUser != null) {
                val userDocRef = firestore.collection("users").document(firebaseUser.uid)
                val userDoc = userDocRef.get().await()

                val user = if (userDoc.exists()) {
                    userDoc.toObject(User::class.java) ?: createDefaultUser(firebaseUser)
                } else {
                    val newUser = User(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email ?: "",
                        name = firebaseUser.displayName ?: "",
                        diabetesType = ""
                    )
                    userDocRef.set(newUser).await()
                    newUser
                }
                emit(Resource.success(user))
            } else {
                emit(Resource.error(AppError.AuthenticationError("Login failed")))
            }
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            emit(Resource.firebaseError(e))
        }
    }

    override fun logout(): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())

        try {
            firebaseAuth.signOut()
            firestore.clearPersistence().await()
            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "Logout failed")
            emit(Resource.firebaseError(e))
        }
    }

    override fun getCurrentUser(): Flow<User?> = callbackFlow {
        val firebaseUser = firebaseAuth.currentUser
        Timber.d("AuthRepositoryImpl: firebaseAuth.currentUser = ${firebaseUser?.uid} email=${firebaseUser?.email}")
        if (firebaseUser == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val userDocRef = firestore.collection("users").document(firebaseUser.uid)

        val listener = userDocRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.w(error, "Listen for user profile failed.")
                trySend(createDefaultUser(firebaseUser))
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                Timber.d("AuthRepositoryImpl: snapshot exists for user ${firebaseUser.uid}: data=${snapshot.data}")
                val user = snapshot.toObject(User::class.java) ?: createDefaultUser(firebaseUser)
                trySend(user)
            } else {
                val newUser = createDefaultUser(firebaseUser)
                userDocRef.set(newUser).addOnSuccessListener {
                    trySend(newUser)
                }.addOnFailureListener {
                    Timber.w(it, "Failed to create user document for the first time.")
                    trySend(newUser)
                }
            }
        }

        awaitClose { listener.remove() }
    }

    override fun updateUserProfile(user: User): Flow<Resource<User>> = flow {
        emit(Resource.loading())

        try {
            firestore.collection("users").document(user.uid).set(user).await()
            emit(Resource.success(user))
        } catch (e: Exception) {
            Timber.e(e, "Profile update failed")
            emit(Resource.firebaseError(e))
        }
    }

    override fun resetPassword(email: String): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())

        try {
            // Send password reset email (Firebase handles it even for non-existent accounts)
            firebaseAuth.sendPasswordResetEmail(email).await()
            Timber.d("Password reset email sent to: $email")
            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "Password reset failed: ${e.message}")
            emit(Resource.firebaseError(e))
        }
    }

    override fun addFavoriteFood(foodId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())
        try {
            val uid = firebaseAuth.currentUser?.uid
            Timber.d("AuthRepositoryImpl.addFavoriteFood: called by uid=$uid for foodId=$foodId")
            if (uid == null) {
                emit(Resource.error(AppError.AuthenticationError("User not logged in")))
                return@flow
            }

            // Update the root array favoriteFoodIds
            firestore.collection("users").document(uid)
                .update("favoriteFoodIds", FieldValue.arrayUnion(foodId))
                .await()
            Timber.d("AuthRepositoryImpl.addFavoriteFood: successfully added foodId=$foodId to user=$uid favoriteFoodIds")

            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "Failed to add favorite food for foodId=$foodId uid=${firebaseAuth.currentUser?.uid}")
            // More detailed logging for Firestore exceptions
            when (e) {
                is com.google.firebase.firestore.FirebaseFirestoreException -> {
                    Timber.w(e, "Firestore error: code=${e.code} message=${e.message}")
                }
                else -> Unit
            }
            emit(Resource.firebaseError(e))
        }
    }

    override fun removeFavoriteFood(foodId: String): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())
        try {
            val uid = firebaseAuth.currentUser?.uid
            Timber.d("AuthRepositoryImpl.removeFavoriteFood: called by uid=$uid to remove foodId=$foodId")
            if (uid == null) {
                emit(Resource.error(AppError.AuthenticationError("User not logged in")))
                return@flow
            }

            // Update the root array favoriteFoodIds
            firestore.collection("users").document(uid)
                .update("favoriteFoodIds", FieldValue.arrayRemove(foodId))
                .await()
            Timber.d("AuthRepositoryImpl.removeFavoriteFood: successfully removed foodId=$foodId from user=$uid favoriteFoodIds")

            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove favorite food for foodId=$foodId uid=${firebaseAuth.currentUser?.uid}")
            when (e) {
                is com.google.firebase.firestore.FirebaseFirestoreException -> {
                    Timber.w(e, "Firestore error: code=${e.code} message=${e.message}")
                }
                else -> Unit
            }
            emit(Resource.firebaseError(e))
        }
    }

    override fun getFavoriteFoodIdsFromSubcollection(): Flow<List<String>> = flow {
        emit(emptyList())
        try {
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                emit(emptyList())
                return@flow
            }

            val snapshot = firestore.collection("users").document(uid)
                .collection("favorites")
                .get()
                .await()

            val ids = snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(com.example.diabite.data.model.UserFavorite::class.java)?.foodId
                } catch (e: Exception) {
                    null
                }
            }
            Timber.d("AuthRepositoryImpl.getFavoriteFoodIdsFromSubcollection: user=$uid found ${ids.size} favorites: $ids")
            emit(ids)
        } catch (e: Exception) {
            Timber.w(e, "getFavoriteFoodIdsFromSubcollection failed")
            emit(emptyList())
        }
    }

    override fun addSearchToHistory(query: String): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())
        try {
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                emit(Resource.success(Unit))
                return@flow
            }

            // Add to history subcollection with timestamp
            val historyEntry = UserHistory(foodId = query, eatenAt = Date())
            firestore.collection("users").document(uid)
                .collection("history")
                .document(query)
                .set(historyEntry)
                .await()

            // Also maintain the root-level `searchHistory` array for UI compatibility
            try {
                firestore.collection("users").document(uid)
                    .update("searchHistory", FieldValue.arrayUnion(query))
                    .await()
                Timber.d("AuthRepositoryImpl.addSearchToHistory: user=$uid pushed $query into root searchHistory array")
            } catch (arrayEx: Exception) {
                Timber.w(arrayEx, "AuthRepositoryImpl.addSearchToHistory: failed to update root searchHistory array for user=$uid")
            }

            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.w(e, "Failed to add search to history")
            emit(Resource.success(Unit))
        }
    }

    override fun clearSearchHistory(): Flow<Resource<Unit>> = flow {
        emit(Resource.loading())
        try {
            val uid = firebaseAuth.currentUser?.uid
            if (uid == null) {
                emit(Resource.success(Unit))
                return@flow
            }

            // Delete all documents in history subcollection
            val historyCollection = firestore.collection("users").document(uid).collection("history")
            val batch = firestore.batch()

            // Get all history documents and delete them
            val historyDocs = historyCollection.get().await()
            historyDocs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()

            // Also clear root-level searchHistory field (for UI compatibility)
            try {
                firestore.collection("users").document(uid)
                    .update("searchHistory", emptyList<String>())
                    .await()
                Timber.d("AuthRepositoryImpl.clearSearchHistory: cleared root searchHistory for user=$uid")
            } catch (e: Exception) {
                Timber.w(e, "AuthRepositoryImpl.clearSearchHistory: failed to clear root searchHistory for user=$uid")
            }

            emit(Resource.success(Unit))
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear search history")
            emit(Resource.firebaseError(e))
        }
    }

    private fun createDefaultUser(firebaseUser: com.google.firebase.auth.FirebaseUser): User {
        return User(
            uid = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName ?: "",
            diabetesType = ""
        )
    }
}
