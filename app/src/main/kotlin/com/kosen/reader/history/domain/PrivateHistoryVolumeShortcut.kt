package com.kosen.reader.history.domain

import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivateHistoryVolumeShortcut @Inject constructor(
	private val settings: AppSettings,
) {

	private val _triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
	val triggers: SharedFlow<Unit> = _triggers.asSharedFlow()

	private val sequenceBuffer = ArrayDeque<Int>(4)
	private var lastKeyTime = 0L

	fun handleKeyEvent(keyCode: Int): Boolean {
		if (!settings.isPrivateHistoryVolumeShortcutEnabled) {
			return false
		}
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			return false
		}
		if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
			return false
		}
		val now = SystemClock.elapsedRealtime()
		if (now - lastKeyTime > SEQUENCE_TIMEOUT_MS) {
			sequenceBuffer.clear()
		}
		lastKeyTime = now
		sequenceBuffer.addLast(keyCode)
		while (sequenceBuffer.size > TARGET_SEQUENCE.size) {
			sequenceBuffer.removeFirst()
		}
		if (sequenceBuffer.size == TARGET_SEQUENCE.size &&
			sequenceBuffer.toIntArray().contentEquals(TARGET_SEQUENCE)
		) {
			sequenceBuffer.clear()
			_triggers.tryEmit(Unit)
		}
		return true
	}

	companion object {
		private val TARGET_SEQUENCE = intArrayOf(
			KeyEvent.KEYCODE_VOLUME_UP,
			KeyEvent.KEYCODE_VOLUME_UP,
			KeyEvent.KEYCODE_VOLUME_DOWN,
			KeyEvent.KEYCODE_VOLUME_DOWN,
		)
		private const val SEQUENCE_TIMEOUT_MS = 2_500L
	}
}
