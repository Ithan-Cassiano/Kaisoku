package com.kosen.reader.core.ui.model

import com.kosen.reader.parsers.model.ContentRating

data class MangaOverride(
	val coverUrl: String?,
	val title: String?,
	val contentRating: ContentRating?,
)
