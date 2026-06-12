package com.kosen.reader.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.list.domain.ListSortOrder
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject

@HiltViewModel
class ContinueReadingViewModel @Inject constructor(
	private val repository: HistoryRepository,
	private val settings: AppSettings,
) : ViewModel() {

	val continueReadingItems = settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE) { incognitoMode }
		.flatMapLatest {
			repository.observeAllWithHistory(
				order = ListSortOrder.LAST_READ,
				filterOptions = emptySet(),
				limit = 64,
			)
		}
		.map { list ->
			list.filter { item ->
				val percent = item.history.percent
				!repository.isHiddenFromMainHistory(item.manga) &&
					percent in 0.01f..0.99f
			}
				.sortedByDescending { it.history.updatedAt }
				.take(12)
				.map { item ->
					val history = item.history
					val fixedPercent = if (ReadingProgress.isCompleted(history.percent)) 1f else history.percent
					ContinueReadingItem(
						manga = item.manga,
						progress = ReadingProgress(
							percent = fixedPercent,
							totalChapters = history.chaptersCount,
							mode = settings.progressIndicatorMode,
						).takeIf { it.isValid() },
						updatedAt = history.updatedAt,
					)
				}
		}
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.WhileSubscribed(5000), emptyList())

	fun hideFromMainHistory(manga: Set<Manga>) {
		viewModelScope.launch(Dispatchers.IO) {
			manga.forEach { item ->
				repository.hideFromMainHistory(item.id)
			}
		}
	}

	fun canHideFromMainHistory(manga: Manga): Boolean =
		repository.canHideFromMainHistory(manga)
}
