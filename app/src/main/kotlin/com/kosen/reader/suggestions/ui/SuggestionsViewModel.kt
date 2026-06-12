package com.kosen.reader.suggestions.ui

import androidx.lifecycle.viewModelScope
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.util.ext.onFirst
import com.kosen.reader.list.domain.MangaListMapper
import com.kosen.reader.list.domain.QuickFilterListener
import com.kosen.reader.list.ui.MangaListViewModel
import com.kosen.reader.list.ui.model.EmptyState
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.list.ui.model.toErrorState
import com.kosen.reader.suggestions.domain.SuggestionRepository
import com.kosen.reader.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.domain.model.LocalManga
import kotlinx.coroutines.flow.SharedFlow

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
	private val repository: SuggestionRepository,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	private val quickFilter: SuggestionsListQuickFilter,
	private val suggestionsScheduler: SuggestionsWorker.Scheduler,
	mangaDataRepository: MangaDataRepository,
	@ApplicationContext private val context: Context,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
	) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges), QuickFilterListener by quickFilter {

	val appliedFilters = quickFilter.appliedOptions

	override val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE_SUGGESTIONS) { suggestionsListMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.suggestionsListMode)

	override val content = combine(
		quickFilter.appliedOptions.combineWithSettings().flatMapLatest { repository.observeAll(0, it) },
		quickFilter.appliedOptions,
		observeListModeWithTriggers(),
	) { list, filters, mode ->
		val header = suggestionsHeader(list.size, filters.isNotEmpty())
		when {
			list.isEmpty() -> if (filters.isEmpty()) {
				listOf(
					header,
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.suggestions_empty_title,
						textSecondary = R.string.text_suggestion_holder,
						actionStringRes = R.string.suggestions_empty_enable_action,
					),
				)
			} else {
				listOfNotNull(
					header,
					quickFilter.filterItem(filters),
					EmptyState(
						icon = R.drawable.ic_empty_common,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_empty_holder_secondary_filtered,
						actionStringRes = 0,
					),
				)
			}

			else -> buildList(list.size + 2) {
				add(header)
				quickFilter.filterItem(filters)?.let(::add)
				mangaListMapper.toListModelList(this, list, mode)
			}
		}
	}.onStart {
		loadingCounter.increment()
	}.onFirst {
		loadingCounter.decrement()
	}.catch {
		emit(listOf(it.toErrorState(canRetry = false)))
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		launchJob(Dispatchers.Default) {
			suggestionsScheduler.schedule()
			if (repository.isEmpty()) {
				suggestionsScheduler.startNow()
			}
		}
	}

	override fun onRefresh() = Unit

	override fun onRetry() = Unit

	fun hasActiveFilters(): Boolean = quickFilter.appliedOptions.value.isNotEmpty()

	fun updateSuggestions() {
		launchJob(Dispatchers.Default) {
			suggestionsScheduler.startNow()
		}
	}

	private fun suggestionsHeader(count: Int, hasFilters: Boolean) = ListHeader(
		textRes = R.string.suggestions,
		filterButtonTextRes = R.string.filter,
		badge = if (count > 0 || hasFilters) {
			context.getString(R.string.suggestions_count, count)
		} else {
			null
		},
	)
}
