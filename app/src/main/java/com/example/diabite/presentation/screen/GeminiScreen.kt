package com.example.diabite.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diabite.presentation.viewmodel.GeminiViewModel
import com.example.diabite.presentation.viewmodel.UiState

@Composable
fun GeminiScreen(
    viewModel: GeminiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var promptText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Input Field
        OutlinedTextField(
            value = promptText,
            onValueChange = { promptText = it },
            label = { Text("Ask Gemini...") },
            modifier = Modifier.fillMaxWidth()
        )

        // Send Button
        Button(
            onClick = {
                if (promptText.isNotBlank()) {
                    viewModel.sendPrompt(promptText)
                }
            },
            enabled = uiState !is UiState.Loading,
            modifier = Modifier.align(Alignment.End)
        ) {
            if (uiState is UiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Generate")
            }
        }

        // Output Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Fill remaining space
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (val state = uiState) {
                    is UiState.Initial -> Text("Enter a prompt to start.")
                    is UiState.Loading -> Text("Thinking...")
                    is UiState.Success -> Text(state.outputText)
                    is UiState.Error -> Text("Error: ${state.errorMessage}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
