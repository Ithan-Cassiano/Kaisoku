package org.koitharu.kotatsu.reader.translation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import javax.inject.Inject

class PageTranslationRenderer @Inject constructor() {

	fun render(
		bitmap: Bitmap,
		blocks: List<PageTranslationBlock>,
		translations: List<String>,
	): Bitmap {
		require(blocks.size == translations.size) { "Translation block count mismatch" }
		val result = bitmap.ensureMutable()
		val canvas = Canvas(result)
		val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.FILL
		}
		val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
			typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
		}
		blocks.zip(translations).forEach { (block, translation) ->
			val text = translation.normalizeText()
			if (text.isBlank()) {
				return@forEach
			}
			drawTranslatedBlock(
				canvas = canvas,
				bitmap = result,
				bounds = block.bounds,
				text = text,
				backgroundPaint = backgroundPaint,
				textPaint = textPaint,
			)
		}
		return result
	}

	private fun drawTranslatedBlock(
		canvas: Canvas,
		bitmap: Bitmap,
		bounds: Rect,
		text: String,
		backgroundPaint: Paint,
		textPaint: TextPaint,
	) {
		val safeBounds = Rect(
			bounds.left.coerceIn(0, bitmap.width - 1),
			bounds.top.coerceIn(0, bitmap.height - 1),
			bounds.right.coerceIn(1, bitmap.width),
			bounds.bottom.coerceIn(1, bitmap.height),
		)
		if (safeBounds.width() < 8 || safeBounds.height() < 8) {
			return
		}
		val backgroundColor = sampleBackgroundColor(bitmap, safeBounds)
		backgroundPaint.color = backgroundColor
		textPaint.color = if (ColorUtils.calculateLuminance(backgroundColor) > 0.45) {
			Color.BLACK
		} else {
			Color.WHITE
		}
		textPaint.textAlign = Paint.Align.CENTER
		val horizontalPadding = max(6f, safeBounds.width() * 0.08f)
		val verticalPadding = max(4f, safeBounds.height() * 0.08f)
		val drawRect = RectF(
			safeBounds.left.toFloat(),
			safeBounds.top.toFloat(),
			safeBounds.right.toFloat(),
			safeBounds.bottom.toFloat(),
		)
		val availableWidth = max(1, (safeBounds.width() - horizontalPadding * 2f).roundToInt())
		val availableHeight = max(1f, safeBounds.height() - verticalPadding * 2f)
		val layout = buildBestLayout(text, textPaint, availableWidth, availableHeight, safeBounds)
		val radius = min(drawRect.width(), drawRect.height()) * 0.12f
		canvas.drawRoundRect(drawRect, radius, radius, backgroundPaint)
		canvas.save()
		canvas.translate(
			safeBounds.left + horizontalPadding,
			safeBounds.top + verticalPadding + max(0f, (availableHeight - layout.height) / 2f),
		)
		layout.draw(canvas)
		canvas.restore()
	}

	private fun buildBestLayout(
		text: String,
		textPaint: TextPaint,
		width: Int,
		availableHeight: Float,
		bounds: Rect,
	): StaticLayout {
		var textSize = min(bounds.height() * 0.42f, bounds.width() * 0.18f).coerceAtLeast(12f)
		var bestLayout = buildLayout(text, textPaint, width, textSize)
		repeat(12) {
			bestLayout = buildLayout(text, textPaint, width, textSize)
			if (bestLayout.height <= availableHeight) {
				return bestLayout
			}
			textSize *= 0.9f
		}
		return bestLayout
	}

	private fun buildLayout(
		text: String,
		textPaint: TextPaint,
		width: Int,
		textSize: Float,
	): StaticLayout {
		textPaint.textSize = textSize
		return StaticLayout.Builder
			.obtain(text, 0, text.length, textPaint, width)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setIncludePad(false)
			.setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
			.setLineSpacing(0f, 1.0f)
			.build()
	}

	private fun sampleBackgroundColor(bitmap: Bitmap, bounds: Rect): Int {
		val probe = Rect(
			(bounds.left - bounds.width() / 10).coerceAtLeast(0),
			(bounds.top - bounds.height() / 10).coerceAtLeast(0),
			(bounds.right + bounds.width() / 10).coerceAtMost(bitmap.width),
			(bounds.bottom + bounds.height() / 10).coerceAtMost(bitmap.height),
		)
		var red = 0L
		var green = 0L
		var blue = 0L
		var count = 0L
		val step = max(1, min(probe.width(), probe.height()) / 12)
		fun sample(x: Int, y: Int) {
			if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) {
				return
			}
			val color = bitmap.getPixel(x, y)
			if (Color.alpha(color) < 16) {
				return
			}
			red += Color.red(color)
			green += Color.green(color)
			blue += Color.blue(color)
			count++
		}
		var x = probe.left
		while (x < probe.right) {
			sample(x, probe.top)
			sample(x, probe.bottom - 1)
			x += step
		}
		var y = probe.top
		while (y < probe.bottom) {
			sample(probe.left, y)
			sample(probe.right - 1, y)
			y += step
		}
		if (count == 0L) {
			return Color.WHITE
		}
		return Color.rgb(
			(red / count).toInt(),
			(green / count).toInt(),
			(blue / count).toInt(),
		)
	}

	private fun Bitmap.ensureMutable(): Bitmap {
		return if (isMutable && config == Bitmap.Config.ARGB_8888) {
			this
		} else {
			copy(Bitmap.Config.ARGB_8888, true)
		}
	}

	private fun String.normalizeText(): String {
		return lineSequence()
			.map { it.trim() }
			.filter { it.isNotEmpty() }
			.joinToString("\n")
	}
}
