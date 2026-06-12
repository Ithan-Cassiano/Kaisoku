package com.kosen.reader.history.domain

import com.kosen.reader.core.os.NetworkState
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.MangaListQuickFilter
import com.kosen.reader.list.ui.model.QuickFilter
import javax.inject.Inject

class HistoryListQuickFilter @Inject constructor(
	private val settings: AppSettings,
	private val repository: HistoryRepository,
	networkState: NetworkState,
) : MangaListQuickFilter(settings) {

	init {
		setFilterOption(ListFilterOption.Downloaded, !networkState.value)
	}

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		add(ListFilterOption.Downloaded)
		if (settings.isTrackerEnabled) {
			add(ListFilterOption.Macro.NEW_CHAPTERS)
		}
		add(ListFilterOption.Macro.UNREAD)
		add(ListFilterOption.Macro.COMPLETED)
		add(ListFilterOption.Macro.READING)
		add(ListFilterOption.Macro.FAVORITE)
		add(ListFilterOption.NOT_FAVORITE)
		if (!settings.isNsfwContentDisabled) {
			add(ListFilterOption.Macro.NSFW)
		}
		repository.getPopularTags(3).mapTo(this) {
			ListFilterOption.Tag(it)
		}
		repository.getPopularSources(Int.MAX_VALUE).mapTo(this) {
			ListFilterOption.Source(it)
		}
	}

	override suspend fun filterItem(
		selectedOptions: Set<ListFilterOption>,
	): QuickFilter? {
		val withoutPrivate = selectedOptions.filter { it != ListFilterOption.Macro.PRIVATE_HISTORY }.toSet()
		val base = super.filterItem(withoutPrivate) ?: return null
		val chips = base.items.filter { chip ->
			chip.data != ListFilterOption.Macro.PRIVATE_HISTORY
		}
		return if (chips.isEmpty()) null else QuickFilter(chips)
	}
}
