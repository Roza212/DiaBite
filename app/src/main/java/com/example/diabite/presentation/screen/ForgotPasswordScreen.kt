package com.example.diabite.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.diabite.common.Route
import com.example.diabite.presentation.viewmodel.AuthViewModel
import com.example.diabite.util.Resource

@Composable
fun ForgotPasswordScreen(navController: NavController, viewModel: AuthViewModel) {

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf(false) }
    var isResetInProgress by remember { mutableStateOf(false) }
    var isResetSuccess by remember { mutableStateOf(false) }
    var hasAttemptedReset by remember { mutableStateOf(false) }

    val passwordResetState by viewModel.passwordResetState.collectAsState()

    // Handle password reset state - only update UI if user has clicked the button
    LaunchedEffect(passwordResetState) {
        if (hasAttemptedReset) {
            when (passwordResetState) {
                is Resource.Success -> {
                    isResetInProgress = false
                    isResetSuccess = true
                }
                is Resource.Error -> {
                    isResetInProgress = false
                    isResetSuccess = false
                    // Error shown in UI - allow user to retry
                }
                is Resource.Loading -> {
                    isResetInProgress = true
                    isResetSuccess = false
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    
    // Trigger initial animation and clear state on screen entry
    LaunchedEffect(Unit) {
        visible = true
        // Reset the password reset state so email form is shown initially
        hasAttemptedReset = false
        viewModel.resetPasswordResetState()
    }

    // Extract colors outside Canvas
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Box(modifier = Modifier.fillMaxSize()) {
        // Decorative Canvas
        Canvas(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            val width = size.width
            val height = size.height
            val radius = 60.dp.toPx()

            drawCircle(
                color = primaryColor.copy(alpha = 0.03f),
                radius = radius,
                center = Offset(width * 0.1f, height * 0.2f)
            )
            drawCircle(
                color = secondaryColor.copy(alpha = 0.03f),
                radius = radius * 0.8f,
                center = Offset(width * 0.9f, height * 0.8f)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(animationSpec = tween(500)) + fadeIn(animationSpec = tween(500))
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.95f),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            IconButton(onClick = { 
                                // Clear password reset state before going back
                                viewModel.resetPasswordResetState()
                                navController.popBackStack() 
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Login",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            "Forgot Password",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                        )

                        Text(
                            "Enter your email to reset password",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (isResetSuccess) {
                            Text(
                                text = "If you have an account with this email, you'll receive a password reset link shortly.\n\nPlease check your inbox.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Button(
                                onClick = { 
                                    // Clear state and navigate back to login
                                    viewModel.resetPasswordResetState()
                                    navController.popBackStack() 
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Back to Login", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // Email Field
                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    emailError = false
                                },
                                label = { Text("Email Address") },
                                placeholder = { Text("Enter your registered email") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = "Email Icon",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                isError = emailError,
                                supportingText = {
                                    if (emailError) {
                                        Text(text = "Valid email is required")
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    focusedLabelColor = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    cursorColor = if (emailError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Reset Button
                            Button(
                                onClick = {
                                    // Check for validation errors
                                    emailError = email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

                                    if (!emailError) {
                                        // Mark that user has attempted reset
                                        hasAttemptedReset = true
                                        // Only proceed if not already in progress
                                        if (!isResetInProgress) {
                                            isResetInProgress = true
                                            viewModel.resetPassword(email)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                // Disable only while in progress, allow retry on error
                                enabled = !isResetInProgress,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                when {
                                    isResetInProgress -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    passwordResetState is Resource.Error -> {
                                        Text("Try Again", fontWeight = FontWeight.Bold)
                                    }
                                    else -> {
                                        Text("Reset Password", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Error message - allows retry
                            if (passwordResetState is Resource.Error) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val error = (passwordResetState as Resource.Error).error
                                Text(
                                    text = error?.userMessage ?: "Failed to send reset email",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
