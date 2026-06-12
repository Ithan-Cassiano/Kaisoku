package com.kosen.reader.history.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import kotlinx.coroutines.Dispatchers
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.parsers.util.isNumeric
import com.kosen.reader.parsers.util.md5
import javax.inject.Inject

@HiltViewModel
class PrivateHistoryPinSetupViewModel @Inject constructor(
	private val settings: AppSettings,
) : BaseViewModel() {

	private val firstPassword = MutableStateFlow<String?>(null)

	val isSecondStep = firstPassword.map { it != null }
		.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Lazily, false)

	val onPasswordSet = MutableEventFlow<Unit>()
	val onPasswordMismatch = MutableEventFlow<Unit>()
	val onClearText = MutableEventFlow<Unit>()

	val isBiometricEnabled
		get() = settings.isPrivateHistoryBiometricEnabled

	fun onNextClick(password: String) {
		if (firstPassword.value == null) {
			firstPassword.value = password
			onClearText.call(Unit)
		} else if (firstPassword.value == password) {
			settings.privateHistoryPinHash = password.md5()
			settings.isPrivateHistoryPinNumeric = password.isNumeric()
			settings.isPrivateHistoryLockEnabled = true
			onPasswordSet.call(Unit)
		} else {
			firstPassword.value = null
			onPasswordMismatch.call(Unit)
			onClearText.call(Unit)
		}
	}

	fun setBiometricEnabled(isEnabled: Boolean) {
		settings.isPrivateHistoryBiometricEnabled = isEnabled
	}
}
