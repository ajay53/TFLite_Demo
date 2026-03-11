package com.example.tflitedemo.presentation

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.Executors

@Composable
fun MediaPipeDetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: MediaPipeObjectDetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Handle permission denied
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize()) {
        MediaPipeLiveDetectionView(
            onFrame = { bitmap, timestamp ->
                viewModel.detectLiveStream(bitmap, timestamp)
            },
            uiState = uiState
        )
    }
}

@Composable
fun MediaPipeLiveDetectionView(
    onFrame: (Bitmap, Long) -> Unit,
    uiState: MediaPipeUiState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        val matrix = Matrix().apply {
                            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                        }
                        val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        onFrame(rotatedBitmap, System.currentTimeMillis())
                        imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        Log.e("MediaPipeLiveView", "Binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        when (val state = uiState) {
            is MediaPipeUiState.Success -> {
                MediaPipeOverlay(state.result)
            }
            is MediaPipeUiState.Error -> {
                Text(text = "Error: ${state.message}", color = Color.Red)
            }
            else -> {}
        }
    }
}

@Composable
fun MediaPipeOverlay(result: ObjectDetectorResult) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        result.detections().forEach { detection ->
            val boundingBox = detection.boundingBox()
            // In a real app, scale coordinates from image size to screen size
            drawRect(
                color = Color.Green,
                topLeft = androidx.compose.ui.geometry.Offset(boundingBox.left, boundingBox.top),
                size = androidx.compose.ui.geometry.Size(boundingBox.width(), boundingBox.height()),
                style = Stroke(width = 4f)
            )
            
            // Draw label
            detection.categories().firstOrNull()?.let { category ->
                // Simplified text drawing could go here
            }
        }
    }
}
