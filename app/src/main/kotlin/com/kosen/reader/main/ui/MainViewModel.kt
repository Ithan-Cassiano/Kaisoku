package com.kosen.reader.main.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import com.kosen.reader.core.exceptions.EmptyHistoryException
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.explore.domain.BrokenSourcesCleanupUseCase
import com.kosen.reader.core.github.AppUpdateRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.prefs.observeAsStateFlow
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.github.AppVersion
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.main.domain.ReadingResumeEnabledUseCase
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.tracker.domain.TrackingRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
	private val historyRepository: HistoryRepository,
	private val appUpdateRepository: AppUpdateRepository,
	trackingRepository: TrackingRepository,
	private val settings: AppSettings,
	readingResumeEnabledUseCase: ReadingResumeEnabledUseCase,
	private val brokenSourcesCleanupUseCase: BrokenSourcesCleanupUseCase,
) : BaseViewModel() {

	val onOpenReader = MutableEventFlow<Manga>()
	val onFirstStart = MutableEventFlow<Unit>()
	val onShowIncognitoModePicker = MutableEventFlow<Unit>()
	val onUpdateAvailable = MutableEventFlow<AppVersion>()
	val onUpdateCheckMessage = MutableEventFlow<String>()

	val isResumeEnabled = readingResumeEnabledUseCase()
		.withErrorHandling()
		.stateIn(
			scope = viewModelScope + Dispatchers.Default,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = false,
		)

	val appUpdate = appUpdateRepository.observeAvailableUpdate()

	val feedCounter = trackingRepository.observeUnreadUpdatesCount()
		.withErrorHandling()
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, 0)

	val isBottomNavPinned = settings.observeAsFlow(
		AppSettings.KEY_NAV_PINNED,
	) {
		isNavBarPinned
	}.flowOn(Dispatchers.Default)

	val isIncognitoModeEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.Default,
		AppSettings.KEY_INCOGNITO_MODE,
		AppSettings.KEY_INCOGNITO_MODE_TYPE,
	) {
		incognitoMode.isActive
	}

	init {
		launchJob {
			val update = appUpdateRepository.fetchUpdate()
			if (update != null) {
				onUpdateAvailable.call(update)
			}
			appUpdateRepository.consumeUpdateCheckMessage()?.let { message ->
				onUpdateCheckMessage.call(message)
			}
		}
		launchJob(Dispatchers.Default) {
			brokenSourcesCleanupUseCase()
		}
		launchJob {
			if (settings.isFirstLaunch) {
				settings.isFirstLaunch = false
				onFirstStart.call(Unit)
			}
		}
	}

	fun openLastReader() {
		launchLoadingJob(Dispatchers.Default) {
			val manga = historyRepository.getLastOrNull() ?: throw EmptyHistoryException()
			onOpenReader.call(manga)
		}
	}

	fun setIncognitoMode(isEnabled: Boolean) {
		settings.incognitoMode = if (isEnabled) IncognitoMode.NO_HISTORY else IncognitoMode.DISABLED
	}

	fun setIncognitoMode(mode: IncognitoMode) {
		settings.incognitoMode = mode
	}

	fun showIncognitoModePicker() {
		onShowIncognitoModePicker.call(Unit)
	}
}
