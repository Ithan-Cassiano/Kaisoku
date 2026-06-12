package com.kosen.reader.reader.ui.pager

import android.content.res.Resources
import com.kosen.reader.core.model.getLocalizedTitle
import com.kosen.reader.parsers.model.MangaChapter

data class ReaderUiState(
	val mangaName: String?,
	val chapter: MangaChapter,
	val chapterIndex: Int,
	val chaptersTotal: Int,
	val currentPage: Int,
	val totalPages: Int,
	val percent: Float,
	val incognito: Boolean,
	val scrollProgress: Float = -1f,
) {

	val chapterNumber: Int
		get() = chapterIndex + 1

	fun hasNextChapter(): Boolean = chapterNumber < chaptersTotal

	fun hasPreviousChapter(): Boolean = chapterIndex > 0

	fun isSliderAvailable(): Boolean = totalPages > 1 && currentPage < totalPages

	fun getChapterTitle(resources: Resources) = chapter.getLocalizedTitle(resources, chapterIndex)
}
