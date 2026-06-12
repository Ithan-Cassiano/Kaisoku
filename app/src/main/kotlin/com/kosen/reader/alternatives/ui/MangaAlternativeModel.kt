package com.kosen.reader.alternatives.ui

import com.kosen.reader.core.model.chaptersCount
import com.kosen.reader.core.model.isSameStoredEntryAs
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaGridModel
import com.kosen.reader.parsers.model.Manga

data class MangaAlternativeModel(
	val mangaModel: MangaGridModel,
	private val referenceChapters: Int,
) : ListModel {

	val manga: Manga
		get() = mangaModel.manga

	val chaptersCount = manga.chaptersCount()

	val chaptersDiff: Int
		get() = if (referenceChapters == 0 || chaptersCount == 0) 0 else chaptersCount - referenceChapters

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaAlternativeModel && other.manga.isSameStoredEntryAs(manga)
	}

	override fun getChangePayload(previousState: ListModel): Any? = if (previousState is MangaAlternativeModel) {
		mangaModel.getChangePayload(previousState.mangaModel)
	} else {
		null
	}
}
