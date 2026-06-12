package com.kosen.reader.history.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.kosen.reader.core.exceptions.WrongPasswordException
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.history.domain.PrivateHistoryLockManager
import javax.inject.Inject

private const val PASSWORD_COMPARE_DELAY = 1_000L

@HiltViewModel
class PrivateHistoryUnlockViewModel @Inject constructor(
	private val settings: AppSettings,
	private val lockManager: PrivateHistoryLockManager,
) : BaseViewModel() {

	private var job: Job? = null

	val onUnlockSuccess = MutableEventFlow<Unit>()

	val isBiometricEnabled: Boolean
		get() = settings.isPrivateHistoryBiometricEnabled

	val isNumericPassword: Boolean
		get() = settings.isPrivateHistoryPinNumeric

	fun tryUnlock(password: String) {
		if (job?.isActive == true) {
			return
		}
		job = launchLoadingJob {
			if (lockManager.verifyPin(password)) {
				onUnlockSuccess.call(Unit)
			} else {
				delay(PASSWORD_COMPARE_DELAY)
				throw WrongPasswordException()
			}
		}
	}

	fun unlockWithDeviceAuth() {
		onUnlockSuccess.call(Unit)
	}
}
