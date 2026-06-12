package com.kosen.reader.favourites.domain

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import com.kosen.reader.core.os.NetworkState
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.MangaListQuickFilter
import com.kosen.reader.parsers.model.ContentType

class FavoritesListQuickFilter @AssistedInject constructor(
	@Assisted private val categoryId: Long,
	private val settings: AppSettings,
	private val repository: FavouritesRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		add(ListFilterOption.NOT_DOWNLOADED)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.COMPLETED)
		add(ListFilterOption.ContentType(ContentType.MANGA))
		add(ListFilterOption.ContentType(ContentType.MANHWA))
		add(ListFilterOption.ContentType(ContentType.MANHUA))
		repository.findPopularTagTitles(categoryId, 3).mapTo(this) {
			ListFilterOption.TagTitle(it)
		}
		repository.findPopularSources(categoryId, Int.MAX_VALUE).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	@AssistedFactory
	interface Factory {

		fun create(categoryId: Long): FavoritesListQuickFilter
	}
}
