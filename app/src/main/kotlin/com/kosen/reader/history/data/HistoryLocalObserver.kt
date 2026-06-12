package com.kosen.reader.history.data

import dagger.Reusable
import com.kosen.reader.core.db.MangaDatabase
import com.kosen.reader.core.db.entity.toManga
import com.kosen.reader.core.db.entity.toMangaTags
import com.kosen.reader.history.domain.model.MangaWithHistory
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.ListSortOrder
import com.kosen.reader.local.data.index.LocalMangaIndex
import com.kosen.reader.local.domain.LocalObserveMapper
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject

@Reusable
class HistoryLocalObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<HistoryWithManga, MangaWithHistory>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	) = db.getHistoryDao().observeAll(order, filterOptions, limit).mapToLocal()

	override fun toManga(e: HistoryWithManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: HistoryWithManga, manga: Manga) = MangaWithHistory(
		manga = manga,
		history = e.history.toMangaHistory(),
	)
}
