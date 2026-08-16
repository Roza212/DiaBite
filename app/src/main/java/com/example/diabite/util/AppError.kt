package com.example.diabite.util

/**
 * Sealed class representing different types of errors in the app
 */
sealed class AppError(
    val code: String,
    val userMessage: String,
    val technicalMessage: String? = null,
    val cause: Throwable? = null,
    val retryable: Boolean = false
) {

    // Network Errors
    class NetworkError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "NETWORK_ERROR",
        userMessage = "Unable to connect. Please check your internet connection and try again.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = true
    )

    class TimeoutError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "TIMEOUT_ERROR",
        userMessage = "Request timed out. Please try again.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = true
    )

    class ServerError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "SERVER_ERROR",
        userMessage = "Server is temporarily unavailable. Please try again later.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = true
    )

    // Firebase Errors
    class FirebasePermissionError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "FIREBASE_PERMISSION_ERROR",
        userMessage = "Access denied. Please check your permissions and try again.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class FirebaseNotFoundError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "FIREBASE_NOT_FOUND_ERROR",
        userMessage = "The requested data could not be found.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class FirebaseQuotaError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "FIREBASE_QUOTA_ERROR",
        userMessage = "Service temporarily unavailable due to high usage. Please try again later.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = true
    )

    // Data Errors
    class InvalidDataError(
        userMessage: String = "Invalid data format. Please try refreshing the data.",
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_DATA_ERROR",
        userMessage = userMessage,
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class MissingFieldError(
        fieldName: String,
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "MISSING_FIELD_ERROR",
        userMessage = "Required information is missing. Please try again.",
        technicalMessage = technicalMessage ?: "Missing field: $fieldName",
        cause = cause,
        retryable = false
    )

    class CorruptedCacheError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "CORRUPTED_CACHE_ERROR",
        userMessage = "Cached data is corrupted. Please clear cache and try again.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    // User Input Errors
    class InvalidSearchQueryError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_SEARCH_QUERY_ERROR",
        userMessage = "Please enter a valid search term.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class InvalidEmailError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_EMAIL_ERROR",
        userMessage = "Please enter a valid email address.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class InvalidPasswordError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_PASSWORD_ERROR",
        userMessage = "Password must be at least 6 characters long.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class InvalidDateError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_DATE_ERROR",
        userMessage = "Please enter a valid date.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    // Authentication Errors
    class AuthenticationError(
        userMessage: String = "Authentication failed. Please check your credentials and try again.",
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "AUTHENTICATION_ERROR",
        userMessage = userMessage,
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class UserNotFoundError(
        userMessage: String = "Account not found. Please check your email or sign up.",
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "USER_NOT_FOUND_ERROR",
        userMessage = userMessage,
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class InvalidCredentialsError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "INVALID_CREDENTIALS_ERROR",
        userMessage = "Invalid email or password",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    class UserAlreadyExistsError(
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "USER_ALREADY_EXISTS_ERROR",
        userMessage = "An account with this email already exists. Please sign in instead.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = false
    )

    // Generic Errors
    class UnknownError(
        technicalMessage: String? = null,
        cause: Throwable? = null,
        retryable: Boolean = false
    ) : AppError(
        code = "UNKNOWN_ERROR",
        userMessage = "An unexpected error occurred. Please try again.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = retryable
    )

    class FeatureNotAvailableError(
        featureName: String,
        technicalMessage: String? = null,
        cause: Throwable? = null
    ) : AppError(
        code = "FEATURE_NOT_AVAILABLE_ERROR",
        userMessage = "$featureName is currently not available. Please try again later.",
        technicalMessage = technicalMessage,
        cause = cause,
        retryable = true
    )

    companion object {
        /**
         * Convert Firebase exceptions to AppError
         */
        fun fromFirebaseException(exception: Exception): AppError {
            val message = exception.message ?: "Unknown Firebase error"
            
            // Try to extract error code from FirebaseAuthException if available
            try {
                if (exception.javaClass.name.contains("FirebaseAuthException")) {
                    val errorCodeMethod = exception.javaClass.getMethod("getErrorCode")
                    val errorCode = errorCodeMethod.invoke(exception) as? String
                    
                    if (errorCode != null) {
                        return when (errorCode) {
                            "INVALID_LOGIN_CREDENTIALS" -> InvalidCredentialsError(message, exception)
                            "INVALID_EMAIL" -> InvalidEmailError(message, exception)
                            "USER_NOT_FOUND" -> UserNotFoundError(technicalMessage = message, cause = exception)
                            "TOO_MANY_REQUESTS" -> AuthenticationError("Too many login attempts. Please try again later.", message, exception)
                            "USER_DISABLED" -> AuthenticationError("This account has been disabled.", message, exception)
                            "WEAK_PASSWORD" -> InvalidPasswordError(message, exception)
                            "EMAIL_ALREADY_IN_USE" -> UserAlreadyExistsError(message, exception)
                            else -> fallbackErrorMapping(message, exception)
                        }
                    }
                }
            } catch (e: Exception) {
                // If reflection fails, fall through to message-based detection
            }
            
            // Fallback to message-based detection
            return fallbackErrorMapping(message, exception)
        }
        
        private fun fallbackErrorMapping(message: String, exception: Exception): AppError {
            return when {
                // Authentication errors - these are the most common login failures
                message.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                message.contains("INVALID_USER_OR_PASSWORD", ignoreCase = true) ||
                message.contains("wrong password", ignoreCase = true) ||
                message.contains("The password is invalid", ignoreCase = true) ||
                message.contains("[INVALID_LOGIN_CREDENTIALS]", ignoreCase = true) ||
                message.contains("Invalid credential", ignoreCase = true) ->
                    InvalidCredentialsError(message, exception)
                
                message.contains("USER_NOT_FOUND", ignoreCase = true) ||
                message.contains("user-not-found", ignoreCase = true) ||
                message.contains("There is no user record", ignoreCase = true) ||
                message.contains("[USER_NOT_FOUND]", ignoreCase = true) ||
                message.contains("no user record", ignoreCase = true) ->
                    UserNotFoundError(technicalMessage = message, cause = exception)
                
                message.contains("INVALID_EMAIL", ignoreCase = true) ||
                message.contains("invalid-email", ignoreCase = true) ||
                message.contains("[INVALID_EMAIL]", ignoreCase = true) ->
                    InvalidEmailError(message, exception)
                
                message.contains("TOO_MANY_REQUESTS", ignoreCase = true) ||
                message.contains("too-many-requests", ignoreCase = true) ||
                message.contains("too many login attempts", ignoreCase = true) ||
                message.contains("[TOO_MANY_REQUESTS]", ignoreCase = true) ->
                    AuthenticationError("Too many login attempts. Please try again later.", message, exception)
                
                message.contains("USER_DISABLED", ignoreCase = true) ||
                message.contains("user-disabled", ignoreCase = true) ||
                message.contains("[USER_DISABLED]", ignoreCase = true) ->
                    AuthenticationError("This account has been disabled.", message, exception)
                
                message.contains("WEAK_PASSWORD", ignoreCase = true) ||
                message.contains("weak-password", ignoreCase = true) ||
                message.contains("[WEAK_PASSWORD]", ignoreCase = true) ->
                    InvalidPasswordError(message, exception)
                
                message.contains("EMAIL_ALREADY_IN_USE", ignoreCase = true) ||
                message.contains("email-already-in-use", ignoreCase = true) ||
                message.contains("[EMAIL_ALREADY_IN_USE]", ignoreCase = true) ->
                    UserAlreadyExistsError(message, exception)
                
                // Firestore and general Firebase errors
                message.contains("PERMISSION_DENIED") || message.contains("permission-denied") ->
                    FirebasePermissionError(message, exception)
                message.contains("NOT_FOUND") || message.contains("not-found") ->
                    FirebaseNotFoundError(message, exception)
                message.contains("RESOURCE_EXHAUSTED") || message.contains("quota") ->
                    FirebaseQuotaError(message, exception)
                message.contains("UNAVAILABLE") || message.contains("unavailable") ->
                    ServerError(message, exception)
                message.contains("DEADLINE_EXCEEDED") || message.contains("timeout") ->
                    TimeoutError(message, exception)
                
                // Network errors
                message.contains("Network error", ignoreCase = true) ||
                message.contains("no internet", ignoreCase = true) ||
                message.contains("unable to connect", ignoreCase = true) ->
                    NetworkError(message, exception)
                
                else -> UnknownError(message, exception, retryable = true)
            }
        }

        /**
         * Convert network exceptions to AppError
         */
        fun fromNetworkException(exception: Exception): AppError {
            val message = exception.message ?: "Unknown network error"

            return when {
                message.contains("timeout", ignoreCase = true) ->
                    TimeoutError(message, exception)
                message.contains("unable to resolve host", ignoreCase = true) ||
                message.contains("no address associated", ignoreCase = true) ->
                    NetworkError(message, exception)
                else -> NetworkError(message, exception)
            }
        }

        /**
         * Convert general exceptions to AppError
         */
        fun fromException(exception: Exception): AppError {
            return UnknownError(
                technicalMessage = exception.message,
                cause = exception,
                retryable = false
            )
        }
    }
}
