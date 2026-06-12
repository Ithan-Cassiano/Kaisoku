package com.kosen.reader.explore.domain

import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.suggestions.ui.SuggestionsWorker
import javax.inject.Inject
import javax.inject.Singleton

private const val TIP_SUGGESTIONS = "suggestions"

@Singleton
class BrokenSourcesCleanupUseCase @Inject constructor(
	private val settings: AppSettings,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val suggestionScheduler: SuggestionsWorker.Scheduler,
) {

	suspend operator fun invoke() {
		if (!settings.isBrokenSourcesCleanupDone) {
			settings.isBrokenSourcesHidden = true
			val brokenSources = MangaParserSource.entries.filter { it.isBroken }
			if (brokenSources.isNotEmpty()) {
				sourcesRepository.setSourcesEnabled(brokenSources, isEnabled = false)
			}
			settings.isBrokenSourcesCleanupDone = true
		}
		if (!settings.isSuggestionsBootstrapDone) {
			settings.isSuggestionsEnabled = true
			settings.closeTip(TIP_SUGGESTIONS)
			suggestionScheduler.schedule()
			suggestionScheduler.startNow()
			settings.isSuggestionsBootstrapDone = true
		}
		if (settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY) {
			historyRepository.reconcilePrivateHistory()
		}
	}
}
