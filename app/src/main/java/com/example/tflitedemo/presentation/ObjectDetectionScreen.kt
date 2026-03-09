package com.example.tflitedemo.presentation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.tflitedemo.domain.DetectionResult

@Composable
fun ObjectDetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: ObjectDetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
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
            viewModel.onPhotoSelected(bitmap)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { launcher.launch("image/*") }) {
            Text("Select Photo")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is ObjectDetectionUiState.Idle -> {
                Text("Select a photo to start detection")
            }
            is ObjectDetectionUiState.Loading -> {
                CircularProgressIndicator()
            }
            is ObjectDetectionUiState.Success -> {
                DetectionResultView(state.bitmap, state.results)
            }
            is ObjectDetectionUiState.Error -> {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
    }
}

@Composable
fun DetectionResultView(bitmap: Bitmap, results: List<DetectionResult>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier
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
                        topLeft = androidx.compose.ui.geometry.Offset(result.boundingBox.left, result.boundingBox.top),
                        size = androidx.compose.ui.geometry.Size(result.boundingBox.width(), result.boundingBox.height()),
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