package com.voiceassistant.engine.ocr

import android.media.Image
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Extracts text from camera [Image] frames using ML Kit Text Recognition.
 */
@Singleton
class CameraOcrEngine @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * @param image camera image (YUV_420_888); caller owns closing the image
     * @param rotationDegrees sensor rotation from CameraX ImageProxy
     */
    suspend fun recognize(image: Image, rotationDegrees: Int): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                val input = InputImage.fromMediaImage(image, rotationDegrees)
                recognizeInput(input)
            }
        }

    suspend fun recognizeInput(input: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(input)
                .addOnSuccessListener { visionText ->
                    cont.resume(visionText.text.trim())
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
}
