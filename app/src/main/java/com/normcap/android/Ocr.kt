package com.normcap.android

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.LatinTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import android.graphics.Bitmap

enum class OcrLang(val label: String) {
    LATIN("Indonesia/Inggris (Latin)"),
    CHINESE("Chinese"),
    JAPANESE("Japanese"),
    KOREAN("Korean"),
}

// ponytail: satu fungsi untuk semua bahasa, tambah opsi kalau ML Kit rilis model baru.
suspend fun recognizeText(bitmap: Bitmap, lang: OcrLang): String {
    val client = when (lang) {
        OcrLang.LATIN -> TextRecognition.getClient(LatinTextRecognizerOptions.Builder().build())
        OcrLang.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrLang.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrLang.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    }
    return try {
        client.process(InputImage.fromBitmap(bitmap, 0)).await().text
    } finally {
        client.close()
    }
}
