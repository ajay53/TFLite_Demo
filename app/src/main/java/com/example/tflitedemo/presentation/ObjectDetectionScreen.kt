package com.example.tflitedemo.presentation

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.tflitedemo.domain.DetectionResult
import java.util.concurrent.Executors

enum class DetectionMode {
    Gallery, Capture, Live
}

@Composable
fun ObjectDetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ObjectDetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(DetectionMode.Gallery) }

    // Permissions
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            mode = DetectionMode.Gallery
        }
    }

    LaunchedEffect(mode) {
        if (mode == DetectionMode.Live || mode == DetectionMode.Capture) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source)
            }.copy(Bitmap.Config.ARGB_8888, true)
            viewModel.onImageUpdate(bitmap)
        }
    }

    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            viewModel.onImageUpdate(it.copy(Bitmap.Config.ARGB_8888, true))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = mode.ordinal) {
            DetectionMode.entries.forEach { detectionMode ->
                Tab(
                    selected = mode == detectionMode,
                    onClick = { mode = detectionMode },
                    text = { Text(detectionMode.name) },
                    icon = {
                        Icon(
                            when (detectionMode) {
                                DetectionMode.Gallery -> Icons.Filled.DateRange
                                DetectionMode.Capture -> Icons.Filled.Face
                                DetectionMode.Live -> Icons.Default.Face
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (mode) {
                DetectionMode.Gallery -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { galleryLauncher.launch("image/*") }) {
                            Text("Select from Gallery")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState is ObjectDetectionUiState.Success) {
                            val state = uiState as ObjectDetectionUiState.Success
                            DetectionResultView(state.bitmap, state.results)
                        }
                    }
                }

                DetectionMode.Capture -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = { captureLauncher.launch() }) {
                            Text("Capture Photo")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState is ObjectDetectionUiState.Success) {
                            val state = uiState as ObjectDetectionUiState.Success
                            DetectionResultView(state.bitmap, state.results)
                        }
                    }
                }

                DetectionMode.Live -> {
//                    LiveDetectionView(onFrame = { viewModel.onImageUpdate(it) }, uiState = uiState)
                }
            }
        }
    }
}

@Composable
fun LiveDetectionView(
    onFrame: (Bitmap) -> Unit,
    uiState: ObjectDetectionUiState
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
                        onFrame(rotatedBitmap)
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
                        Log.e("LiveDetectionView", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState is ObjectDetectionUiState.Success) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val results = uiState.results
                // Note: Scaling logic is simplified here. 
                // In a production app, you'd calculate the scale between the camera frame and the screen size.
                results.forEach { result ->
                    drawRect(
                        color = Color.Red,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            result.boundingBox.left,
                            result.boundingBox.top
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            result.boundingBox.width(),
                            result.boundingBox.height()
                        ),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
    }
}

@Composable
fun DetectionResultView(bitmap: Bitmap, results: List<DetectionResult>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selected Photo",
                modifier = Modifier.fillMaxSize()
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                results.forEach { result ->
                    drawRect(
                        color = Color.Red,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            result.boundingBox.left,
                            result.boundingBox.top
                        ),
                        size = androidx.compose.ui.geometry.Size(
                            result.boundingBox.width(),
                            result.boundingBox.height()
                        ),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        results.forEach { result ->
            Text("${result.label}: ${(result.confidence * 100).toInt()}%")
        }
    }
}
