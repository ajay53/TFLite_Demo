package com.example.tflitedemo.domain

import android.graphics.Bitmap
import android.graphics.RectF

data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

interface ObjectDetector {

    suspend fun initModel()
    suspend fun detect(bitmap: Bitmap): List<DetectionResult>
    fun close()
}