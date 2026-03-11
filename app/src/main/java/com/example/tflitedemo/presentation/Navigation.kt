package com.example.tflitedemo.presentation

sealed class Screen(val route: String, val title: String) {
    object ObjectDetection : Screen("object_detection", "Object Detection")
    object MediaPipeDetection : Screen("mediapipe_detection", "MediaPipe Detection")
    object FutureFeature2 : Screen("future_feature_2", "Future Feature 2")
}