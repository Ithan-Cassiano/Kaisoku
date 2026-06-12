package com.kosen.reader.history.ui

import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.model.Manga
import java.time.Instant

data class ContinueReadingItem(
	val manga: Manga,
	val progress: ReadingProgress?,
	val updatedAt: Instant,
)
