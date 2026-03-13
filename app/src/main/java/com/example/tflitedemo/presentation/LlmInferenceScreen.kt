package com.example.tflitedemo.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LlmInferenceScreen(
    modifier: Modifier = Modifier,
    viewModel: LlmInferenceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LLM Text Summarization",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Enter text to summarize") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
            enabled = uiState == LlmUiState.Ready || uiState is LlmUiState.Success
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.summarize(inputText) },
            enabled = (uiState == LlmUiState.Ready || uiState is LlmUiState.Success) && inputText.isNotBlank()
        ) {
            Text("Summarize")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (val state = uiState) {
            is LlmUiState.Initializing -> {
                CircularProgressIndicator()
                Text("Initializing LLM Model...")
            }
            is LlmUiState.Processing -> {
                CircularProgressIndicator()
                Text("Generating Summary...")
            }
            is LlmUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Summary:", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = state.output)
                    }
                }
            }
            is LlmUiState.Error -> {
                Text(text = "Error: ${state.message}", color = Color.Red)
            }
            is LlmUiState.Ready -> {
                Text("LLM is ready.")
            }
            else -> {}
        }
    }
}