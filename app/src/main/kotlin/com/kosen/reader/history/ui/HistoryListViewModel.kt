package com.kosen.reader.history.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import com.kosen.reader.R
import com.kosen.reader.core.model.MangaHistory
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.core.prefs.ListMode
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.prefs.observeAsStateFlow
import com.kosen.reader.core.ui.util.ReversibleAction
import com.kosen.reader.core.ui.util.ReversibleHandle
import com.kosen.reader.core.util.ext.calculateTimeAgo
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.core.util.ext.flattenLatest
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.history.domain.HistoryListQuickFilter
import com.kosen.reader.history.domain.MarkAsReadUseCase
import com.kosen.reader.history.domain.PrivateHistoryAdminUseCase
import com.kosen.reader.history.domain.model.MangaWithHistory
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.ListSortOrder
import com.kosen.reader.list.domain.MangaListMapper
import com.kosen.reader.list.domain.QuickFilterListener
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.list.ui.MangaListViewModel
import com.kosen.reader.list.ui.model.EmptyState
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.list.ui.model.toErrorState
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.stats.data.StatsRepository
import com.kosen.reader.stats.domain.StatsPeriod
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay

private const val PAGE_SIZE = 16

@HiltViewModel
class HistoryListViewModel @Inject constructor(
	private val repository: HistoryRepository,
	private val settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val markAsReadUseCase: MarkAsReadUseCase,
	private val quickFilter: HistoryListQuickFilter,
	private val statsRepository: StatsRepository,
	private val privateHistoryAdminUseCase: PrivateHistoryAdminUseCase,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	private val sortOrder: StateFlow<ListSortOrder> = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_HISTORY_ORDER,
		valueProducer = { historySortOrder },
	)

	override val listMode = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_LIST_MODE_HISTORY,
		valueProducer = { historyListMode },
	)

	private val isGroupingEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_HISTORY_GROUPING,
		valueProducer = { isHistoryGroupingEnabled },
	).combine(sortOrder) { g, s ->
		g && s.isGroupingSupported()
	}

	private val limit = MutableStateFlow(PAGE_SIZE)
	private val refreshSignal = MutableStateFlow(0)
	private val isPaginationReady = AtomicBoolean(false)
	private var contentJob: Job? = null

	val isStatsEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_STATS_ENABLED,
		valueProducer = { isStatsEnabled },
	)

	val isPrivateHistoryFilterActive = quickFilter.appliedOptions
		.map { ListFilterOption.Macro.PRIVATE_HISTORY in it }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), false)

	val weeklyReadingMinutes: StateFlow<Long?> = combine(
		isStatsEnabled,
		refreshSignal,
	) { enabled, _ -> enabled }
		.flatMapLatest { enabled ->
			if (!enabled) {
				flowOf<Long?>(null)
			} else {
				flow {
					val stats = statsRepository.getReadingStats(StatsPeriod.WEEK, emptySet())
					val totalMs = stats.sumOf { it.duration }
					emit(if (totalMs > 0) totalMs / 60_000 else null)
				}
			}
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), null)

	override val content = MutableStateFlow<List<ListModel>>(listOf(LoadingState))

	init {
		val appliedFilters = quickFilter.appliedOptions
		val history = observeHistoryFlow(
			sortOrder = sortOrder,
			filters = appliedFilters.combineWithSettings(),
			limit = limit,
			repository = repository,
			isPaginationReady = isPaginationReady,
		)
		val contentState = content
		contentJob = createHistoryContentFlow(
			appliedFilters = appliedFilters,
			history = history,
			isGroupingEnabled = isGroupingEnabled,
			listMode = observeListModeWithTriggers(),
			sortOrder = sortOrder,
			quickFilter = quickFilter,
			mangaListMapper = mangaListMapper,
			isPaginationReady = isPaginationReady,
		).onEach { contentState.value = it }
			.launchIn(viewModelScope + Dispatchers.Default)
		settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE_TYPE) { incognitoMode }
			.onEach { mode ->
				if (mode != IncognitoMode.HIDDEN_HISTORY) {
					quickFilter.clearPrivateHistoryFilter()
				}
			}
			.launchIn(viewModelScope + Dispatchers.Default)
	}

	override fun onCleared() {
		contentJob?.cancel()
		contentJob = null
		super.onCleared()
	}

	override fun onRefresh() {
		launchLoadingJob(Dispatchers.Default) {
			delay(350)
			if (limit.value > PAGE_SIZE) {
				limit.value = PAGE_SIZE
			}
			refreshSignal.update { it + 1 }
		}
	}

	override fun onRetry() = Unit

	fun clearHistory(minDate: Instant?) {
		launchJob(Dispatchers.Default) {
			val stringRes = if (minDate == null) {
				repository.clear()
				R.string.history_cleared
			} else {
				repository.deleteAfter(minDate.toEpochMilli())
				R.string.removed_from_history
			}
			onActionDone.call(ReversibleAction(stringRes, null))
		}
	}

	fun removeNotFavorite() {
		launchJob(Dispatchers.Default) {
			repository.deleteNotFavorite()
			onActionDone.call(ReversibleAction(R.string.removed_from_history, null))
		}
	}

	fun removeFromHistory(ids: Set<Long>) {
		if (ids.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val handle = repository.delete(ids)
			onActionDone.call(ReversibleAction(R.string.removed_from_history, handle))
		}
	}

	fun hideFromMainHistory(manga: Set<Manga>) {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY || manga.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val ids = manga.map { it.id }
			for (id in ids) {
				repository.hideFromMainHistory(id)
			}
			onActionDone.call(
				ReversibleAction(
					R.string.hidden_from_main_history,
					ReversibleHandle {
						for (id in ids) {
							repository.showInMainHistory(id)
						}
					},
				),
			)
		}
	}

	fun showInMainHistory(manga: Set<Manga>) {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY || manga.isEmpty()) {
			return
		}
		launchJob(Dispatchers.Default) {
			val ids = manga.filter { repository.canShowInMainHistory(it) }.map { it.id }
			if (ids.isEmpty()) {
				return@launchJob
			}
			for (id in ids) {
				repository.showInMainHistory(id)
			}
			onActionDone.call(
				ReversibleAction(
					R.string.shown_in_main_history,
					ReversibleHandle {
						for (id in ids) {
							repository.hideFromMainHistory(id)
						}
					},
				),
			)
		}
	}

	fun isHiddenHistoryModeEnabled(): Boolean =
		settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY

	fun canHideFromMainHistory(manga: Manga): Boolean =
		repository.canHideFromMainHistory(manga)

	fun canShowInMainHistory(manga: Manga): Boolean =
		repository.canShowInMainHistory(manga)

	fun markAsRead(items: Set<Manga>) {
		launchLoadingJob(Dispatchers.Default) {
			markAsReadUseCase(items)
		}
	}

	fun hasActiveFilters(): Boolean = quickFilter.appliedOptions.value.isNotEmpty()

	fun clearPrivateHistoryFilter() {
		quickFilter.clearPrivateHistoryFilter()
		refreshSignal.update { it + 1 }
	}

	fun requestMoreItems() {
		if (isPaginationReady.compareAndSet(true, false)) {
			limit.value += PAGE_SIZE
		}
	}

	fun applyWeeklyReadFilter() {
		setFilterOption(ListFilterOption.Macro.WEEKLY_READ, true)
	}

	fun hideAllFromSourceOf(manga: Manga) {
		launchJob(Dispatchers.Default) {
			val count = privateHistoryAdminUseCase.hideAllFromSource(manga.source.name)
			if (count > 0) {
				refreshSignal.update { it + 1 }
			}
		}
	}

	fun hideAllWithTagOf(manga: Manga) {
		val tag = manga.tags.firstOrNull()?.title ?: return
		launchJob(Dispatchers.Default) {
			val count = privateHistoryAdminUseCase.hideAllWithTag(tag)
			if (count > 0) {
				refreshSignal.update { it + 1 }
			}
		}
	}

	fun exportPrivateHistory(uri: android.net.Uri) {
		launchLoadingJob(Dispatchers.IO) {
			privateHistoryAdminUseCase.exportPrivateHistory(uri)
		}
	}

	fun clearPrivateHistory() {
		launchJob(Dispatchers.Default) {
			privateHistoryAdminUseCase.clearPrivateHistory()
			refreshSignal.update { it + 1 }
		}
	}
}

private fun observeHistoryFlow(
	sortOrder: Flow<ListSortOrder>,
	filters: Flow<Set<ListFilterOption>>,
	limit: Flow<Int>,
	repository: HistoryRepository,
	isPaginationReady: AtomicBoolean,
): Flow<List<MangaWithHistory>> = combine(
	sortOrder,
	filters,
	limit,
) { order, filters, limit ->
	isPaginationReady.set(false)
	repository.observeAllWithHistory(order, filters, limit)
}.flattenLatest()

private fun createHistoryContentFlow(
	appliedFilters: Flow<Set<ListFilterOption>>,
	history: Flow<List<MangaWithHistory>>,
	isGroupingEnabled: Flow<Boolean>,
	listMode: Flow<ListMode>,
	sortOrder: Flow<ListSortOrder>,
	quickFilter: HistoryListQuickFilter,
	mangaListMapper: MangaListMapper,
	isPaginationReady: AtomicBoolean,
): Flow<List<ListModel>> = com.kosen.reader.core.util.ext.combine(
	appliedFilters,
	history,
	isGroupingEnabled,
	listMode,
	sortOrder,
) { filters, list, grouped, mode, order ->
		mapHistoryContent(
			list = list,
			grouped = grouped,
			mode = mode,
			filters = filters,
			sortOrder = order,
			quickFilter = quickFilter,
			mangaListMapper = mangaListMapper,
		)
}.distinctUntilChanged().onEach {
	isPaginationReady.set(true)
}.catch { e ->
	emit(listOf(e.toErrorState(canRetry = false)))
}

private suspend fun mapHistoryContent(
	list: List<MangaWithHistory>,
	grouped: Boolean,
	mode: ListMode,
	filters: Set<ListFilterOption>,
	sortOrder: ListSortOrder,
	quickFilter: HistoryListQuickFilter,
	mangaListMapper: MangaListMapper,
): List<ListModel> {
	if (list.isEmpty()) {
		return if (filters.isEmpty()) {
			listOf(historyEmptyState(hasFilters = false))
		} else {
			listOfNotNull(quickFilter.filterItem(filters), historyEmptyState(hasFilters = true))
		}
	}
	val result = ArrayList<ListModel>((if (grouped) (list.size * 1.4).toInt() else list.size) + 2)
	quickFilter.filterItem(filters)?.let(result::add)
	val mangaModels = mangaListMapper.toListModelList(
		manga = list.mapTo(ArrayList(list.size)) { it.manga },
		mode = mode,
	)
	var prevHeader: ListHeader? = null
	var isEmpty = true
	for (index in list.indices) {
		val history = list[index].history
		isEmpty = false
		if (grouped) {
			val header = history.header(sortOrder)
			if (header != prevHeader) {
				if (header != null) {
					result += header
				}
				prevHeader = header
			}
		}
		result += mangaModels[index]
	}
	if (filters.isNotEmpty() && isEmpty) {
		result += historyEmptyState(hasFilters = true)
	}
	return result
}

private fun MangaHistory.header(order: ListSortOrder): ListHeader? = when (order) {
	ListSortOrder.LAST_READ,
	ListSortOrder.LONG_AGO_READ -> calculateTimeAgo(updatedAt)?.let {
		ListHeader(it)
	} ?: ListHeader(R.string.unknown)

	ListSortOrder.OLDEST,
	ListSortOrder.NEWEST -> calculateTimeAgo(createdAt)?.let {
		ListHeader(it)
	} ?: ListHeader(R.string.unknown)

	ListSortOrder.UNREAD,
	ListSortOrder.PROGRESS -> ListHeader(
		when {
			ReadingProgress.isCompleted(percent) -> R.string.status_completed
			percent in 0f..0.01f -> R.string.status_planned
			percent in 0f..1f -> R.string.status_reading
			else -> R.string.unknown
		},
	)

	ListSortOrder.ALPHABETIC,
	ListSortOrder.ALPHABETIC_REVERSE,
	ListSortOrder.RELEVANCE,
	ListSortOrder.NEW_CHAPTERS,
	ListSortOrder.UNREAD_CHAPTERS,
	ListSortOrder.UNREAD_CHAPTERS_REVERSE,
	ListSortOrder.UPDATED,
	ListSortOrder.RATING -> null
}

private fun historyEmptyState(hasFilters: Boolean) = if (hasFilters) {
	EmptyState(
		icon = R.drawable.ic_empty_history,
		textPrimary = R.string.nothing_found,
		textSecondary = R.string.text_empty_holder_secondary_filtered,
		actionStringRes = R.string.reset_filter,
	)
} else {
	EmptyState(
		icon = R.drawable.ic_empty_history,
		textPrimary = R.string.text_history_holder_primary,
		textSecondary = R.string.text_history_holder_secondary,
		actionStringRes = R.string.history_empty_explore_action,
	)
}
