package com.kosen.reader.favourites.domain

import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import com.kosen.reader.core.db.MangaDatabase
import com.kosen.reader.core.db.entity.toManga
import com.kosen.reader.core.db.entity.toMangaTags
import com.kosen.reader.favourites.data.FavouriteManga
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.ListSortOrder
import com.kosen.reader.local.data.index.LocalMangaIndex
import com.kosen.reader.local.domain.LocalObserveMapper
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject

@Reusable
class LocalFavoritesObserver @Inject constructor(
	localMangaIndex: LocalMangaIndex,
	private val db: MangaDatabase,
) : LocalObserveMapper<FavouriteManga, Manga>(localMangaIndex) {

	fun observeAll(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(order, filterOptions, limit).mapToLocal()

	fun observeAll(
		categoryId: Long,
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<Manga>> = db.getFavouritesDao().observeAll(categoryId, order, filterOptions, limit).mapToLocal()

	override fun toManga(e: FavouriteManga) = e.manga.toManga(e.tags.toMangaTags(), null)

	override fun toResult(e: FavouriteManga, manga: Manga) = manga
}
