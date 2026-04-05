package org.koitharu.kotatsu.reader.translation

import android.graphics.Rect

data class PageTranslationBlock(
	val text: String,
	val bounds: Rect,
)
