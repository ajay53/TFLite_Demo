package com.example.tflitedemo.presentation

sealed class Screen(val route: String, val title: String) {
    object ObjectDetection : Screen("object_detection", "Object Detection")
    object FutureFeature1 : Screen("future_feature_1", "Future Feature 1")
    object FutureFeature2 : Screen("future_feature_2", "Future Feature 2")
}