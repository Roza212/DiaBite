package com.example.diabite.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.diabite.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.pow

@HiltViewModel
class GeminiViewModel @Inject constructor() : ViewModel() {

    // Initialize the Model with gemini-2.0-flash-exp as recommended
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.5-flash-lite",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // Manage UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Function to call API with retry logic
    fun sendPrompt(inputText: String, retryCount: Int = 0) {
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val response = generativeModel.generateContent(inputText)
                response.text?.let { output ->
                    _uiState.value = UiState.Success(output)
                } ?: run {
                    _uiState.value = UiState.Error("No response text found")
                }
            } catch (e: kotlinx.serialization.MissingFieldException) {
                // Handle serialization errors specifically
                println("DEBUG: Serialization error caught: ${e.message}")
                _uiState.value = UiState.Error("AI service temporarily unavailable. Please try again in a moment.")
            } catch (e: Exception) {
                handleGeminiError(e, inputText, retryCount)
            }
        }
    }

    private fun handleGeminiError(exception: Exception, inputText: String, retryCount: Int) {
        val errorMessage = exception.message ?: "Unknown error"
        val localizedMessage = exception.localizedMessage ?: errorMessage

        println("DEBUG: Handling Gemini error: $errorMessage")

        // Handle specific Gemini API errors
        when {
            // Quota exceeded (free tier limits)
            errorMessage.contains("quota exceeded") ||
            errorMessage.contains("exceeded your current quota") ||
            errorMessage.contains("please check your plan and billing details") ||
            localizedMessage.contains("quota exceeded") ||
            localizedMessage.contains("exceeded your current quota") -> {
                println("DEBUG: Quota exceeded detected")
                _uiState.value = UiState.Error("AI free tier quota exceeded. Please upgrade your Gemini API plan or wait for daily quota reset.")
            }

            // Rate limiting (429)
            errorMessage.contains("429") ||
            errorMessage.contains("resource exhausted") ||
            localizedMessage.contains("429") ||
            localizedMessage.contains("resource exhausted") -> {
                println("DEBUG: Rate limit detected, retryCount: $retryCount")
                if (retryCount < 2) {
                    // Retry with exponential backoff
                    val delayMs = (1000L * (2.0.pow(retryCount))).toLong()
                    _uiState.value = UiState.Error("Rate limit exceeded. Retrying in ${delayMs/1000}s...")

                    viewModelScope.launch {
                        delay(delayMs)
                        sendPrompt(inputText, retryCount + 1)
                    }
                } else {
                    _uiState.value = UiState.Error("API rate limit exceeded. Please wait a few minutes before trying again.")
                }
            }

            // Network errors
            errorMessage.contains("network") ||
            errorMessage.contains("timeout") ||
            errorMessage.contains("unavailable") ||
            localizedMessage.contains("network") ||
            localizedMessage.contains("timeout") ||
            localizedMessage.contains("unavailable") -> {
                println("DEBUG: Network error detected")
                if (retryCount < 1) {
                    _uiState.value = UiState.Error("Network error. Retrying...")

                    viewModelScope.launch {
                        delay(5000L)
                        sendPrompt(inputText, retryCount + 1)
                    }
                } else {
                    _uiState.value = UiState.Error("Network connection failed. Please check your internet connection and try again.")
                }
            }

            // Serialization errors (missing fields)
            exception is kotlinx.serialization.MissingFieldException ||
            errorMessage.contains("MissingFieldException") ||
            errorMessage.contains("serialization") ||
            localizedMessage.contains("MissingFieldException") ||
            localizedMessage.contains("serialization") -> {
                println("DEBUG: Serialization error in handleGeminiError")
                _uiState.value = UiState.Error("AI service temporarily unavailable. Please try again in a moment.")
            }

            // Authentication errors
            errorMessage.contains("unauthenticated") ||
            errorMessage.contains("permission") ||
            localizedMessage.contains("unauthenticated") ||
            localizedMessage.contains("permission") -> {
                _uiState.value = UiState.Error("Authentication failed. Please check your API key configuration.")
            }

            // Generic API errors
            else -> {
                println("DEBUG: Generic error: $errorMessage")
                _uiState.value = UiState.Error("AI service error: ${getUserFriendlyError(localizedMessage)}")
            }
        }
    }

    private fun getUserFriendlyError(error: String): String {
        return when {
            error.contains("invalid") -> "Invalid request. Please try rephrasing your question."
            error.contains("internal") -> "AI service is experiencing issues. Please try again later."
            error.contains("deadline") -> "Request timed out. Please try again."
            else -> "An unexpected error occurred. Please try again."
        }
    }
}

// Simple State Sealed Class
sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val outputText: String) : UiState()
    data class Error(val errorMessage: String) : UiState()
}
