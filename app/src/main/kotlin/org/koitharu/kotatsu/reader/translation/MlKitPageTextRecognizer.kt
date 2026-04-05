package org.koitharu.kotatsu.reader.translation

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject

class MlKitPageTextRecognizer @Inject constructor() {

	private val latinRecognizer by lazy {
		TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
	}

	private val japaneseRecognizer by lazy {
		TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
	}

	suspend fun recognize(
		bitmap: Bitmap,
		mode: PageTranslationOcrMode,
	): List<PageTranslationBlock> = when (mode) {
		PageTranslationOcrMode.AUTO -> {
			val startedAt = SystemClock.elapsedRealtime()
			val japanese = recognizeWith(japaneseRecognizer, bitmap)
			val latin = recognizeWith(latinRecognizer, bitmap)
			val selected = if (japanese.score() >= latin.score()) japanese else latin
			Log.d(
				TAG,
				"AUTO OCR finished in ${SystemClock.elapsedRealtime() - startedAt}ms japanese=${japanese.size}/${japanese.score()} latin=${latin.size}/${latin.score()} selected=${if (selected === japanese) "JAPANESE" else "LATIN"}",
			)
			selected
		}

		PageTranslationOcrMode.JAPANESE -> recognizeWith(japaneseRecognizer, bitmap).also {
			Log.d(TAG, "JAPANESE OCR finished blocks=${it.size} score=${it.score()}")
		}
		PageTranslationOcrMode.LATIN -> recognizeWith(latinRecognizer, bitmap).also {
			Log.d(TAG, "LATIN OCR finished blocks=${it.size} score=${it.score()}")
		}
	}

	private suspend fun recognizeWith(
		recognizer: TextRecognizer,
		bitmap: Bitmap,
	): List<PageTranslationBlock> {
		val image = InputImage.fromBitmap(bitmap, 0)
		val result = recognizer.process(image).await()
		return result.textBlocks.mapNotNull { block ->
			val bounds = block.boundingBox ?: return@mapNotNull null
			val text = block.text
				.lineSequence()
				.map { it.trim() }
				.filter { it.isNotEmpty() }
				.joinToString("\n")
			if (!text.isUseful() || bounds.width() < 24 || bounds.height() < 12) {
				return@mapNotNull null
			}
			PageTranslationBlock(
				text = text,
				bounds = Rect(bounds),
			)
		}.sortedWith(compareBy<PageTranslationBlock> { it.bounds.top }.thenBy { it.bounds.left })
	}

	private fun List<PageTranslationBlock>.score(): Int {
		return sumOf { block -> block.text.count { !it.isWhitespace() } }
	}

	private fun String.isUseful(): Boolean {
		val compact = filterNot(Char::isWhitespace)
		return compact.length >= 2 && compact.any(Char::isLetterOrDigit)
	}

	private companion object {
		private const val TAG = "PageTranslation"
	}
}
