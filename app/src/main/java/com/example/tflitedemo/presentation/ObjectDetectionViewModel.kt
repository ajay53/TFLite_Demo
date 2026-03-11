package com.example.tflitedemo.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tflitedemo.domain.DetectionResult
import com.example.tflitedemo.domain.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ObjectDetectionViewModel @Inject constructor(
    private val objectDetector: ObjectDetector
) : ViewModel() {

    init {
        viewModelScope.launch {
            objectDetector.initModel()
        }
    }

    private val _uiState = MutableStateFlow<ObjectDetectionUiState>(ObjectDetectionUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun onPhotoSelected(bitmap: Bitmap) {
        viewModelScope.launch (Dispatchers.IO) {
            _uiState.value = ObjectDetectionUiState.Loading
            try {
                val results = objectDetector.detect(bitmap)
                _uiState.value = ObjectDetectionUiState.Success(bitmap, results)
            } catch (e: Exception) {
                _uiState.value = ObjectDetectionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun onImageUpdate(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val results = objectDetector.detect(bitmap)
            try {
                _uiState.value = ObjectDetectionUiState.Success(bitmap, results)
            } catch (e: Exception) {
                _uiState.value = ObjectDetectionUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        objectDetector.close()
    }
}

sealed interface ObjectDetectionUiState {
    object Idle : ObjectDetectionUiState
    object Loading : ObjectDetectionUiState
    data class Success(val bitmap: Bitmap, val results: List<DetectionResult>) : ObjectDetectionUiState
    data class Error(val message: String) : ObjectDetectionUiState
}