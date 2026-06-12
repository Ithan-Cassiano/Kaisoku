package com.kosen.reader.tracker.ui.updates

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import com.kosen.reader.R
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.ListMode
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.ui.model.DateTimeAgo
import com.kosen.reader.core.util.ext.calculateTimeAgo
import com.kosen.reader.core.util.ext.onFirst
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.MangaListMapper
import com.kosen.reader.list.domain.QuickFilterListener
import com.kosen.reader.list.ui.MangaListViewModel
import com.kosen.reader.list.ui.model.EmptyState
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.list.ui.model.toErrorState
import com.kosen.reader.tracker.domain.TrackingRepository
import com.kosen.reader.tracker.domain.UpdatesListQuickFilter
import com.kosen.reader.tracker.domain.model.MangaTracking
import javax.inject.Inject
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel
class UpdatesViewModel @Inject constructor(
	private val repository: TrackingRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: UpdatesListQuickFilter,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	override val content = combine(
		quickFilter.appliedOptions.flatMapLatest { filterOptions ->
			repository.observeUpdatedManga(
				limit = 0,
				filterOptions = filterOptions,
			)
		},
		quickFilter.appliedOptions,
		settings.observeAsFlow(AppSettings.KEY_UPDATED_GROUPING) { isUpdatedGroupingEnabled },
		observeListModeWithTriggers(),
	) { mangaList, filters, grouping, mode ->
		when {
			mangaList.isEmpty() -> listOfNotNull(
				quickFilter.filterItem(filters),
				EmptyState(
					icon = R.drawable.ic_empty_history,
					textPrimary = R.string.text_history_holder_primary,
					textSecondary = R.string.text_history_holder_secondary,
					actionStringRes = 0,
				),
			)

			else -> mangaList.toUi(mode, filters, grouping)
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(
		viewModelScope + Dispatchers.Default,
		SharingStarted.Eagerly,
		listOf(LoadingState),
	)

	init {
		launchJob(Dispatchers.Default) {
			repository.gc()
		}
	}

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun remove(ids: Set<Long>) {
		launchJob(Dispatchers.Default) {
			repository.clearUpdates(ids)
		}
	}

	private suspend fun List<MangaTracking>.toUi(
		mode: ListMode,
		filters: Set<ListFilterOption>,
		grouped: Boolean,
	): List<ListModel> {
		val result = ArrayList<ListModel>(if (grouped) (size * 1.4).toInt() else size + 1)
		quickFilter.filterItem(filters)?.let(result::add)
		val mangaModels = mangaListMapper.toListModelList(
			manga = mapTo(ArrayList(size)) { it.manga },
			mode = mode,
		)
		var prevHeader: DateTimeAgo? = null
		for (index in indices) {
			val item = this[index]
			if (grouped) {
				val header = item.lastChapterDate?.let { calculateTimeAgo(it) }
				if (header != prevHeader) {
					if (header != null) {
						result += ListHeader(header)
					}
					prevHeader = header
				}
			}
			result += mangaModels[index]
		}
		return result
	}

}
