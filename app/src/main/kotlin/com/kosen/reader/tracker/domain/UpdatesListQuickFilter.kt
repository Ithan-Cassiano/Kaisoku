package com.kosen.reader.tracker.domain

import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.favourites.domain.FavouritesRepository
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.MangaListQuickFilter
import javax.inject.Inject

class UpdatesListQuickFilter @Inject constructor(
	private val favouritesRepository: FavouritesRepository,
	settings: AppSettings,
) : MangaListQuickFilter(settings) {

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> =
		favouritesRepository.getMostUpdatedCategories(
			limit = 4,
		).map {
			ListFilterOption.Favorite(it)
		}
}
