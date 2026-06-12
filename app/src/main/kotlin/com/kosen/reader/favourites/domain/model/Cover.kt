package com.kosen.reader.favourites.domain.model

import com.kosen.reader.core.model.MangaSource

data class Cover(
	val url: String?,
	val source: String,
) {
	val mangaSource by lazy { MangaSource(source) }
}
