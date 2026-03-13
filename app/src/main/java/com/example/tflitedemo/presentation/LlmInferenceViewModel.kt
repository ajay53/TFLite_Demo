package com.example.tflitedemo.presentation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LlmInferenceViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "LlmInferenceViewModel"
    }

    private var llmInference: LlmInference? = null

    private val _uiState = MutableStateFlow<LlmUiState>(LlmUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _partialResponse = MutableStateFlow("")
    val partialResponse = _partialResponse.asStateFlow()

    init {
        setupLlm()
    }

    private fun setupLlm() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = LlmUiState.Initializing
            try {
                // The .task file should be in the assets or a specific path
                // For this example, we assume it's named 'model.task' in assets
//                val modelPath = File(context.cacheDir, "model.task").absolutePath
                val modelPath = "/data/local/tmp/llm/gemma_1b_int4.task"

                // Note: In a real app, you'd need to ensure the file exists at this path
                // either by downloading it or copying it from assets if it's small enough.

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(512)
//                    .setMaxTokens(64)
//                    .setTopK(40)
//                    .setTemperature(0.8f)
//                    .setRandomSeed(101)
                    .build()

                llmInference = LlmInference.createFromOptions(context, options)
                _uiState.value = LlmUiState.Ready
            } catch (e: Exception) {

                Log.d(TAG, "setupLlm: $e")
                _uiState.value = LlmUiState.Error(e.message ?: "Failed to initialize LLM")
            }
        }
    }

    fun summarize(text: String) {
//        val prompt = "Summarize the following text:\n\n$text"
//        generateResponse(prompt)

        // Heuristic check: Gemma/Llama models usually have ~4 chars per token.
        // If maxTokens is 1024, we should limit input to roughly 700 tokens
        // to leave room for the model's response.
        val maxInputChars = 700 * 4

        if (text.length > maxInputChars) {
            _uiState.value = LlmUiState.Error("Text is too long to summarize (approx ${text.length / 4} tokens). Please provide a shorter snippet.")
            return
        }

        val prompt = "Summarize the following text concisely:\n\n$text"
        generateResponse(prompt)

    }

    private fun generateResponse(prompt: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = LlmUiState.Processing
            _partialResponse.value = ""
            try {
                llmInference?.generateResponse(prompt)?.let { response ->
                    _uiState.value = LlmUiState.Success(response)
                } ?: run {
                    _uiState.value = LlmUiState.Error("LLM not initialized")
                }
            } catch (e: Exception) {
                _uiState.value = LlmUiState.Error(e.message ?: "Generation failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llmInference?.close()
    }
}

sealed interface LlmUiState {
    object Idle : LlmUiState
    object Initializing : LlmUiState
    object Ready : LlmUiState
    object Processing : LlmUiState
    data class Success(val output: String) : LlmUiState
    data class Error(val message: String) : LlmUiState
}