package com.kosen.reader.suggestions.domain

import androidx.annotation.FloatRange
import com.kosen.reader.parsers.model.Manga

data class MangaSuggestion(
	val manga: Manga,
	@FloatRange(from = 0.0, to = 1.0)
	val relevance: Float,
)