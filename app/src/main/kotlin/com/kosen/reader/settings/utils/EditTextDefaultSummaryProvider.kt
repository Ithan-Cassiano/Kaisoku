package com.kosen.reader.settings.utils

import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.kosen.reader.R
import com.kosen.reader.parsers.util.ifNullOrEmpty

class EditTextDefaultSummaryProvider(
	private val defaultValue: String,
) : Preference.SummaryProvider<EditTextPreference> {

	override fun provideSummary(
		preference: EditTextPreference,
	): CharSequence = preference.text.ifNullOrEmpty {
		preference.context.getString(R.string.default_s, defaultValue)
	}
}
