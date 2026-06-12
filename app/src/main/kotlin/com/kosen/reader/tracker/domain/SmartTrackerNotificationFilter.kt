package com.kosen.reader.tracker.domain

import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.favourites.domain.FavouritesRepository
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartTrackerNotificationFilter @Inject constructor(
	private val settings: AppSettings,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
) {

	suspend fun shouldNotify(manga: Manga): Boolean {
		if (!settings.isTrackerSmartNotificationsEnabled) {
			return true
		}
		val history = historyRepository.getOne(manga)
		if (history != null && history.percent > 0f) {
			return true
		}
		if (favouritesRepository.isInReadLaterCategory(manga.id)) {
			return true
		}
		return false
	}
}
