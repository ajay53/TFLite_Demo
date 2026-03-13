package com.example.tflitedemo.presentation

sealed class Screen(val route: String, val title: String) {
    object ObjectDetection : Screen("object_detection", "Object Detection")
    object MediaPipeDetection : Screen("mediapipe_detection", "MediaPipe Detection")
    object LlmInference : Screen("llm_inference", "LLM Summarization")
}