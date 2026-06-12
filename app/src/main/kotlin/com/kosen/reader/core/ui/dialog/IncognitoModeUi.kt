package com.kosen.reader.core.ui.dialog

import android.content.Context
import androidx.annotation.UiContext
import com.kosen.reader.R
import com.kosen.reader.core.prefs.IncognitoMode

fun showIncognitoModePicker(
	@UiContext context: Context,
	currentMode: IncognitoMode,
	onSelected: (IncognitoMode) -> Unit,
) {
	val modes = IncognitoMode.entries
	val labels = modes.map { context.getString(it.titleResId) }.toTypedArray()
	val checkedItem = modes.indexOf(currentMode).coerceAtLeast(0)
	buildAlertDialog(context, isCentered = true) {
		setTitle(R.string.incognito_mode)
		setSingleChoiceItems(labels, checkedItem) { dialog, which ->
			onSelected(modes[which])
			dialog.dismiss()
		}
		setNegativeButton(android.R.string.cancel, null)
	}.show()
}
