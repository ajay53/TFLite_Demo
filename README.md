# TFLite & MediaPipe Demo App

A modern Android application demonstrating on-device AI capabilities using **LiteRT (TensorFlow Lite)**, **MediaPipe Vision**, and **MediaPipe GenAI**. Built with **Jetpack Compose**, **Hilt**, and **SOLID** principles.

## 🚀 Features

1.  **Object Detection (LiteRT)**: 
    *   Supports Gallery selection, Camera capture, and Live camera feed.
    *   Uses the latest LiteRT (2.1.0) and LiteRT Support libraries.
2.  **Object Detection (MediaPipe)**:
    *   Real-time live stream detection using MediaPipe Tasks Vision.
    *   Clean separation of concerns with a dedicated ViewModel.
3.  **LLM Text Summarization (GenAI)**:
    *   On-device Large Language Model inference using MediaPipe GenAI.
    *   Uses `.task` files (e.g., Gemma 2B) for private, offline summarization.

---

## 🛠 Setup Instructions

### 1. Download TFLite Models
Before running the app, you must download the required TFLite models. This is handled by a custom Gradle task.

Run the following command in your terminal:
```bash
./gradlew downloadDeeplab
```
*Note: This task is integrated into the `preBuild` step, so a standard build should also trigger it.*

### 2. Local LLM Model Setup (Gemma/LiteRT)

To run Large Language Models on-device, the `.task` model file must be accessible within the app's private data directory. Follow these steps to sideload the model during development.

#### Step A: Push Model to Device
Upload the converted `.task` file from your workstation to a temporary directory on the Android device.

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push <LOCAL_PATH_TO_TASK_FILE> /data/local/tmp/llm/gemma_1b_int4.task
```

#### Step B: Grant Read Permissions
Grant "World Read" permissions so the app can copy the file.

```bash
adb shell chmod 666 /data/local/tmp/llm/gemma_1b_int4.task
```

#### Step C: Move to App Private Storage
Move the model into the app's internal `files` directory. This is required for memory mapping (`mmap`) to function correctly.

```bash
# Replace com.example.tflitedemo with your package name if changed
adb shell "run-as com.example.tflitedemo cp /data/local/tmp/llm/gemma_1b_int4.task /data/user/0/com.example.tflitedemo/files/model.task"
```

#### Step D: Verify Deployment
Confirm the file exists and check the size.

```bash
adb shell "run-as com.example.tflitedemo ls -lh /data/user/0/com.example.tflitedemo/files/"
```

---

## 🛠 Troubleshooting Initialization

| Error | Cause | Solution |
| --- | --- | --- |
| `errno=13` | Permission Denied | Ensure you ran `chmod 666` and copied to the internal `files/` directory. |
| `STABLEHLO` Opcode | Version Mismatch | Match your model's LiteRT compiler version with the library version in `libs.versions.toml`. |
| `Failed to init` | RAM Exhaustion | 2B+ models may need > 8GB RAM. Use 4-bit quantization and check `android:largeHeap="true"`. |

---

## 🏗 Architecture (Senior Level)

*   **Clean Architecture**: Separation into `domain`, `data`, and `presentation` layers.
*   **Dependency Injection**: Powered by **Hilt** for scalable and testable code.
*   **Reactive UI**: State-driven UI using Compose `StateFlow` and `collectAsState`.
*   **CameraX**: High-performance camera integration for real-time vision tasks.

### Production Note
In a production environment, manual model sideloading is replaced by a **Model Downloader Service** that fetches models from remote storage (like Firebase or S3) and verifies checksums before initialization.
