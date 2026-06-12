package com.kosen.reader.list.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import com.kosen.reader.core.model.isNsfw
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.ListMode
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.prefs.observeAsStateFlow
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.ui.util.ReversibleAction
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.domain.model.LocalManga

abstract class MangaListViewModel(
	private val settings: AppSettings,
	private val mangaDataRepository: MangaDataRepository,
	@param:LocalStorageChanges private val localStorageChanges: SharedFlow<LocalManga?>,
) : BaseViewModel() {

	abstract val content: StateFlow<List<ListModel>>
	open val listMode = settings.observeAsFlow(AppSettings.KEY_LIST_MODE) { listMode }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, settings.listMode)
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val gridScale = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		key = AppSettings.KEY_GRID_SIZE,
		valueProducer = { gridSize / 100f },
	)

	val isIncognitoModeEnabled: Boolean
		get() = settings.isIncognitoModeEnabled

	abstract fun onRefresh()

	abstract fun onRetry()

	protected fun List<Manga>.skipNsfwIfNeeded() = if (settings.isNsfwContentDisabled) {
		filterNot { it.isNsfw() }
	} else {
		this
	}

	protected fun Flow<Set<ListFilterOption>>.combineWithSettings(): Flow<Set<ListFilterOption>> = combine(
		settings.observeAsFlow(AppSettings.KEY_DISABLE_NSFW) { isNsfwContentDisabled },
	) { filters, skipNsfw ->
		if (skipNsfw) {
			filters + ListFilterOption.SFW
		} else {
			filters
		}
	}

	protected fun observeListModeWithTriggers(): Flow<ListMode> = combine(
		listMode,
		merge(
			mangaDataRepository.observeOverridesTrigger(emitInitialState = true),
			mangaDataRepository.observeFavoritesTrigger(emitInitialState = true),
			localStorageChanges.onStart { emit(null) },
		).map { Unit }
			.conflate()
			.debounce(TRIGGERS_DEBOUNCE_MS),
		settings.observeChanges().filter { key ->
			key == AppSettings.KEY_PROGRESS_INDICATORS
				|| key == AppSettings.KEY_TRACKER_ENABLED
				|| key == AppSettings.KEY_QUICK_FILTER
				|| key == AppSettings.KEY_MANGA_LIST_BADGES
				|| key == AppSettings.KEY_PRIVATE_HISTORY_IDS
				|| key == AppSettings.KEY_INCOGNITO_MODE_TYPE
				|| key == AppSettings.KEY_PRIVATE_HISTORY_VOLUME_SHORTCUT
		}.onStart { emit("") }
			.map { Unit }
			.conflate(),
	) { mode, _, _ ->
		mode
	}

	private companion object {
		private const val TRIGGERS_DEBOUNCE_MS = 120L
	}
}
