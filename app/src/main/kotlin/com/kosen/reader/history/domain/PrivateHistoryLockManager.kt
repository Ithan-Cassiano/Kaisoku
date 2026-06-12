package com.kosen.reader.history.domain

import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.parsers.util.md5
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateHistoryLockManager @Inject constructor(
	private val settings: AppSettings,
) {

	val isLockEnabled: Boolean
		get() = settings.isPrivateHistoryLockEnabled && settings.hasPrivateHistoryPin()

	fun isUnlocked(sessionId: String): Boolean =
		!isLockEnabled || settings.isPrivateHistoryUnlocked(sessionId)

	fun markUnlocked(sessionId: String) {
		settings.markPrivateHistoryUnlocked(sessionId)
	}

	fun lock(sessionId: String) {
		settings.lockPrivateHistory(sessionId)
	}

	fun verifyPin(pin: String): Boolean =
		settings.privateHistoryPinHash == pin.md5()
}
