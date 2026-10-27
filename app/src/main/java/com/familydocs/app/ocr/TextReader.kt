package com.familydocs.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** On-device OCR with ML Kit (bundled Latin model, works offline, nothing leaves the phone). */
class TextReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(pages: List<Bitmap>): String =
        pages.joinToString("\n") { recognizer.process(InputImage.fromBitmap(it, 0)).await().text }

    suspend fun readAndParse(pages: List<Bitmap>): Pair<String, DocumentParser.Result> {
        val text = read(pages)
        return text to DocumentParser.parse(text)
    }
}
