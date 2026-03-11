package com.example.tflitedemo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.scale
import com.example.tflitedemo.domain.DetectionResult
import com.example.tflitedemo.domain.ObjectDetector
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.metadata.MetadataExtractor
import javax.inject.Inject

class ObjectDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ObjectDetector {

    companion object {
        private const val TAG = "ObjectDetectorImpl"
    }

//    lateinit var metadataExtractor: MetadataExtractor

    // Inside ObjectDetectorImpl
    private val inputImageSize = 448
//    private val dataSize = inputImageSize * inputImageSize * 3

    // In a real app, you would initialize TFLite interpreter here
    lateinit var model: CompiledModel
//    =
    /*CompiledModel.create(
        context.assets,
        "efficientdet-lite2.tflite",
        CompiledModel.Options(Accelerator.CPU),
        null,
    )*/

//    org.tensorflow.lite.support.tensorbuffe
//    val abc: org.tensorflow.lite.support.tensorbuffer.TensorBuffer


    // These are com.google.ai.edge.litert.TensorBuffer objects
//    private val inputBuffers = model.createInputBuffers()
//    private val outputBuffers = model.createOutputBuffers()

    lateinit var inputBuffers: List<TensorBuffer>

    //    val input = org.tensorflow.lite.support.tensorbuffer.TensorBuffer
    lateinit var outputBuffers: List<TensorBuffer>

//    val metadataExtractor = MetadataExtractor(model.)

    // Labels mapping (You should ideally load this from a .txt file in assets)
    private val labels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    override suspend fun initModel() {
        model =
            CompiledModel.create(
                context.assets,
                "efficientdet-lite2.tflite",
                CompiledModel.Options(Accelerator.CPU),
                null,
            )

        inputBuffers = model.createInputBuffers()

        outputBuffers = model.createOutputBuffers()
    }

    override suspend fun detect(bitmap: Bitmap): List<DetectionResult> {
        val startTime = System.currentTimeMillis()

        // --- 1. PREPROCESSING ---
        val preProcessStart = System.currentTimeMillis()

        // EfficientDet-Lite2 expects 448x448.
        // EfficientDet-Lite2 usually expects FLOAT32 [0, 1] normalization.
        // If your model is UINT8, change DataType below and remove NormalizeOp.
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageSize, inputImageSize, ResizeOp.ResizeMethod.BILINEAR))
            // Normalize: (value - mean) / std.
            // To get [0, 1]: mean=0, std=255.
            // To get [-1, 1]: mean=127.5, std=127.5
//            .add(NormalizeOp(0f, 255f))
//            .add(CastOp(DataType.UINT8)) // Cast back to the model's expected type
            .build()

        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(bitmap)
        val processedImage = imageProcessor.process(tensorImage)

        // Loading the buffer into LiteRT

        // region Convert Image to FloatArray
        // 1. Get the ByteBuffer from your processed TensorImage
        val byteBuffer = processedImage.buffer
        byteBuffer.rewind() // Ensure you start from the beginning

        // 2. Create a FloatBuffer view of the data
        val floatBuffer = byteBuffer.asFloatBuffer()
        val intBuffer = byteBuffer.asIntBuffer()


        // 3. Create a FloatArray and copy the data into it
        val floatArray = FloatArray(floatBuffer.limit())
        floatBuffer.get(floatArray)

        val intArray = IntArray(intBuffer.limit())
        intBuffer.get(intArray)


        // 4. Write the FloatArray to your LiteRT TensorBuffer
        inputBuffers[0].writeInt(intArray)
//        inputBuffers[0].writeFloat(floatArray)

        // endregion

        val preProcessEnd = System.currentTimeMillis()

        // --- 2. INFERENCE ---
        val inferenceStart = System.currentTimeMillis()

        model.run(inputBuffers, outputBuffers)

        val inferenceEnd = System.currentTimeMillis()

        // --- 3. POST-PROCESSING ---
        val postProcessStart = System.currentTimeMillis()

        val locations = outputBuffers[0].readFloat()
        val classes = outputBuffers[1].readFloat()
        val scores = outputBuffers[2].readFloat()
        val numDetectionsArray = outputBuffers[3].readFloat()
        val numDetections =
            if (numDetectionsArray.isNotEmpty()) numDetectionsArray[0].toInt() else 0

        val results = mutableListOf<DetectionResult>()

        // Limit loop to numDetections to avoid processing empty buffer space
        for (i in 0 until numDetections) {
            if (scores[i] >= 0.5f) {
                val label = labels.getOrNull(classes[i].toInt()) ?: "Unknown"

                // EfficientDet boxes: [ymin, xmin, ymax, xmax]
                val boundingBox = RectF(
                    locations[i * 4 + 1] * bitmap.width,  // xmin -> left
                    locations[i * 4] * bitmap.height,     // ymin -> top
                    locations[i * 4 + 3] * bitmap.width,  // xmax -> right
                    locations[i * 4 + 2] * bitmap.height  // ymax -> bottom
                )

                results.add(DetectionResult(label, scores[i], boundingBox))
            }
        }

        val postProcessEnd = System.currentTimeMillis()
        val totalTime = System.currentTimeMillis() - startTime

        // --- LOGGING ---
        Log.d(TAG, """
        Performance Stats:
        - Pre-processing:  ${preProcessEnd - preProcessStart}ms
        - Inference:       ${inferenceEnd - inferenceStart}ms
        - Post-processing: ${postProcessEnd - postProcessStart}ms
        - Total Latency:   ${totalTime}ms
        - Objects Found:   ${results.size}
    """.trimIndent())
        return results
        // ---------------------------

        // 4. Post-processing
        // FIX: LiteRT 2.1.0 uses readFloatArray() for multiple values, not readFloat()

    }

    override fun close() {
        // Close TFLite interpreter
        model.close()
    }
}