package com.kosen.reader.history.domain.model

import com.kosen.reader.core.model.MangaHistory
import com.kosen.reader.parsers.model.Manga

data class MangaWithHistory(
	val manga: Manga,
	val history: MangaHistory
)
