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
        // 1. Preprocessing
        // EfficientDet-Lite2 expects 448x448.
        // Most EfficientDet FLOAT models expect [0, 1] normalization.
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

        // Get the processed buffer
        val processedBuffer = processedImage.buffer

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


// Ensure the position is at 0 before copying
//        processedBuffer.rewind()

// Transfer the data to your input buffer
//        inputBuffers[0].writeFloat(processedBuffer)


//        val (h, w) = Pair(256, 256)
//        val (h, w) = Pair(448, 448)

//        var image = processedImage.bitmap.scale(w, h, true)

//        val inputFloatArray = normalize(processedImage.bitmap, 127.5f, 127.5f)

//        inputBuffers[0].writeFloat(inputFloatArray)
//        inputBuffers[0].writeFloat(inputFloatArray)


        model.run(inputBuffers, outputBuffers)

//        val outputFloatArray = outputBuffers[0].readFloat()
//        val outputBuffer = FloatBuffer.wrap(outputFloatArray)

        // 4. Post-processing
        // FIX: LiteRT 2.1.0 uses readFloatArray() for multiple values, not readFloat()
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

        Log.d(TAG, "detect: found ${results.size} objects")
        return results
    }


    //    private fun normalize(tensorImage: TensorImage, mean: Float, stddev: Float): FloatArray {
    private fun normalize(image: Bitmap, mean: Float, stddev: Float): FloatArray {

//        val image = tensorImage.bitmap

        val width = image.width
        val height = image.height
        val numPixels = width * height
        val pixelsIntArray = IntArray(numPixels)
        val outputFloatArray = FloatArray(numPixels * 3) // 3 channels (R, G, B)

        image.getPixels(pixelsIntArray, 0, width, 0, 0, width, height)

        for (i in 0 until numPixels) {
            val pixel = pixelsIntArray[i]

            // Extract channels (ARGB_8888 format assumed)
            val (r, g, b) =
                Triple(
                    Color.red(pixel).toFloat(),
                    Color.green(pixel).toFloat(),
                    Color.blue(pixel).toFloat(),
                )

            // Normalize and store in interleaved format
            val outputBaseIndex = i * 3
            outputFloatArray[outputBaseIndex + 0] = (r - mean) / stddev // Red
            outputFloatArray[outputBaseIndex + 1] = (g - mean) / stddev // Green
            outputFloatArray[outputBaseIndex + 2] = (b - mean) / stddev // Blue
        }

        return outputFloatArray
    }

    private fun getMetadata() {
        // 1. Load the model file from assets
//        val fileDescriptor = assets.openFd("model.tflite")
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
// This is your Bytebuffer
//        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

// 2. Create the extractor from your model buffer
//        val metadataExtractor = MetadataExtractor(modelBuffer)

// 3. Use the modelBuffer for the Interpreter
//        val interpreter = Interpreter(modelBuffer)

//_________________________

//        // 1. Create the extractor from your model buffer
//        val metadataExtractor = MetadataExtractor(modelBuffer)
//
//// 2. Check if metadata even exists
//        if (metadataExtractor.hasMetadata()) {
//
//            // 3. Get Input Normalization (Mean and Std)
//            val inputMetadata = metadataExtractor.getInputTensorMetadata(0)
//            val processUnits = inputMetadata.processUnits(0) // Usually where Normalization lives
//            // Note: You may need to parse the FlatBuffer objects here for specific float values
//
//            // 4. Get Labels from the packed files
//            val labelFileStream = metadataExtractor.getAssociatedFile("labels.txt")
//            val labels = labelFileStream.bufferedReader().readLines()
//
//            Log.d("LiteRT", "Loaded ${labels.size} labels directly from model!")
//        }
    }

    override fun close() {
        // Close TFLite interpreter
        model.close()
    }
}