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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import java.util.concurrent.Executors

private const val TAG = "MediaPipeDetectionScree"

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
                        /*Log.d(
                            "MediaPipeLiveDetectionView",
                            "rotatedBitmap Size ${rotatedBitmap.width}x${rotatedBitmap.height}"
                        )*/
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
fun MediaPipeOverlay(
    result: ObjectDetectorResult,
    // Use the EXACT size from your logs
    bitmapWidth: Float = 480f,
    bitmapHeight: Float = 640f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        result.detections().forEach { detection ->
            val box = detection.boundingBox()

            // 1. Convert Pixel coordinates from the 480x640 bitmap to 0.0-1.0 Ratio
            val leftRatio = box.left / bitmapWidth
            val topRatio = box.top / bitmapHeight
            val widthRatio = box.width() / bitmapWidth
            val heightRatio = box.height() / bitmapHeight

            // 2. Map those Ratios to your current Screen size
            val left = leftRatio * size.width
            val top = topRatio * size.height
            val width = widthRatio * size.width
            val height = heightRatio * size.height

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4.dp.toPx())
            )

            // 3. Draw Label & Score
            detection.categories().firstOrNull()?.let { category ->
                val text = "${category.categoryName()} ${(category.score() * 100).toInt()}%"

                drawContext.canvas.nativeCanvas.apply {
                    val textPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 42f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // Measure text to draw a background box
                    val textBounds = android.graphics.Rect()
                    textPaint.getTextBounds(text, 0, text.length, textBounds)

                    val textPadding = 10f
                    val bgTop = top - textBounds.height() - (textPadding * 2)

                    // Draw Background for Text
                    drawRect(
                        left,
                        bgTop,
                        left + textBounds.width() + (textPadding * 2),
                        top,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            alpha = 160 // Semi-transparent
                        }
                    )

                    // Draw the actual Text
                    drawText(text, left + textPadding, top - textPadding, textPaint)
                }
            }
            // Log for debugging
            // Log.d("Overlay", "Screen: ${size.width}x${size.height}, Box: $left, $top")
        }
    }
}


/*@Composable
fun MediaPipeOverlay(
    result: ObjectDetectorResult,
    // Use the exact dimensions the MODEL expects (your 448 from earlier)
    modelInputWidth: Float = 448f,
    modelInputHeight: Float = 448f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Calculate the ratio between the Screen and the Model Input
        val scaleX = size.width / modelInputWidth
        val scaleY = size.height / modelInputHeight

        result.detections().forEach { detection ->
            val box = detection.boundingBox()

            // Scale the pixel coordinates to your screen
            val left = box.left * scaleX
            val width = box.width() * scaleX

            // ADJUSTMENT: If the object looks "too low",
            // it's often because the model's 0.0 top isn't the screen's 0.0 top.
            val top = box.top * scaleY
            val height = box.height() * scaleY

//            val top = box.top * scaleY
//            val height = box.height() * scaleY

            Log.d(TAG, "MediaPipeOverlay: left: $left, top: $top, width: $width, height: $height")

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}*/


/*@Composable
fun MediaPipeOverlay(
    result: ObjectDetectorResult,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Canvas(modifier = modifier) {
        result.detections().forEach { detection ->
            val boundingBox = detection.boundingBox()

            Log.d(TAG, "MediaPipeOverlay: boundingBox: $boundingBox")

            // MediaPipe bounding boxes are often normalized (0.0 to 1.0)
            // Multiply directly by the Canvas width and height
            val left = boundingBox.left.coerceIn(0f, 1f) * size.width
//            val left = boundingBox.left * size.width
            val top = boundingBox.top * size.height
            val width = boundingBox.width() * size.width
            val height = boundingBox.height() * size.height

            Log.d(TAG, "MediaPipeOverlay: left: $left, top: $top, width: $width, height: $height")

            // 1. Draw the Bounding Box
            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 4.dp.toPx())
            )

            // 2. Draw Label and Confidence
            detection.categories().firstOrNull()?.let { category ->
                val text = "${category.categoryName()} ${(category.score() * 100).toInt()}%"

                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GREEN
                        textSize = 40f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    // Draw text slightly above the box
                    drawText(text, left, top - 10f, paint)
                }
            }
        }
    }
}*/


/*@Composable
fun MediaPipeOverlay(
    result: ObjectDetectorResult,
    imageWidth: Int = 448,  // Pass the actual width of the bitmap analyzed
    imageHeight: Int = 448  // Pass the actual height of the bitmap analyzed
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Calculate scale factors between the analyzed image and the screen
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        result.detections().forEach { detection ->
            val boundingBox = detection.boundingBox()

            // 1. Scale coordinates to screen size
            val left = boundingBox.left * scaleX
            val top = boundingBox.top * scaleY
            val width = boundingBox.width() * scaleX
            val height = boundingBox.height() * scaleY

            // 2. Draw the Green Box
            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 8f)
            )

            // 3. Draw Label and Confidence Percent
            detection.categories().firstOrNull()?.let { category ->
                val text = "${category.categoryName()} ${(category.score() * 100).toInt()}%"

                // Use nativeCanvas for text drawing as Compose Canvas text is more complex
                drawContext.canvas.nativeCanvas.apply {
                    val paint = Paint().apply {
                        color = Color.Green
                        textSize = 40

//                        isFakeBoldText = true
                    }

                    // Draw a small background for the text to make it readable
                    val textBounds = Rect()
                    paint.getTextBounds(text, 0, text.length, textBounds)

                    drawRect(
                        left,
                        top - textBounds.height() - 10f,
                        left + textBounds.width() + 20f,
                        top,
                        Paint().apply { color = android.graphics.Color.BLACK; alpha = 160 }
                    )

                    drawText(text, left + 10f, top - 10f, paint)
                }
            }
        }
    }
}*/

/*@Composable
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
}*/
