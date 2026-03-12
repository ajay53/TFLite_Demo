package com.example.tflitedemo.presentation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MediaPipeObjectDetectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "MediaPipeViewmodel"
    }

    private var objectDetector: ObjectDetector? = null

    private val _uiState = MutableStateFlow<MediaPipeUiState>(MediaPipeUiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        val baseOptionsBuilder =
            BaseOptions.builder()
                .setModelAssetPath("efficientdet-lite2.tflite")
                .setDelegate(Delegate.CPU)
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setScoreThreshold(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)

        try {
            objectDetector = ObjectDetector.createFromOptions(context, optionsBuilder.build())
        } catch (e: Exception) {
            _uiState.value = MediaPipeUiState.Error(e.message ?: "Failed to initialize detector")
        }
    }

    fun detectLiveStream(bitmap: Bitmap, timestamp: Long) {
        val mpImage = BitmapImageBuilder(bitmap).build()
        objectDetector?.detectAsync(mpImage, timestamp)
    }

    private fun returnLivestreamResult(
        result: ObjectDetectorResult,
        input: com.google.mediapipe.framework.image.MPImage
    ) {
        /*if (result.detections().isNotEmpty()) {
            Log.d(TAG, "returnLivestreamResult: ${result.detections()}")
        }*/
        _uiState.value = MediaPipeUiState.Success(result)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        _uiState.value = MediaPipeUiState.Error(error.message ?: "Unknown error")
    }

    override fun onCleared() {
        super.onCleared()
        objectDetector?.close()
    }
}

sealed interface MediaPipeUiState {
    object Idle : MediaPipeUiState
    data class Success(val result: ObjectDetectorResult) : MediaPipeUiState
    data class Error(val message: String) : MediaPipeUiState
}