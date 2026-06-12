package com.kosen.reader.core.prefs

import androidx.annotation.StringRes
import com.kosen.reader.R

enum class IncognitoMode(@StringRes val titleResId: Int) {
	DISABLED(R.string.incognito_mode_disabled),
	NO_HISTORY(R.string.incognito_mode_no_history),
	HIDDEN_HISTORY(R.string.incognito_mode_hidden_history),
	;

	val savesHistory: Boolean
		get() = this == HIDDEN_HISTORY

	val skipsHistory: Boolean
		get() = this == NO_HISTORY

	val isActive: Boolean
		get() = this != DISABLED
}
