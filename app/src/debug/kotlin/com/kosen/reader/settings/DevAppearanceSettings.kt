package com.kosen.reader.settings

import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.kosen.reader.R
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.main.ui.DevTabBackground

object DevAppearanceSettings {

	fun bind(fragment: AppearanceSettingsFragment) {
		val screen = fragment.preferenceScreen
		val localePref = fragment.findPreference<Preference>(AppSettings.KEY_APP_LOCALE) ?: return
		val context = fragment.requireContext()
		val switch = SwitchPreferenceCompat(context).apply {
			key = AppSettings.KEY_DEV_TAB_BACKGROUND
			title = context.getString(R.string.dev_tab_background)
			summary = context.getString(R.string.dev_tab_background_summary)
			isChecked = fragment.settings.isDevTabBackgroundEnabled
			order = localePref.order + 1
			setOnPreferenceChangeListener { _, newValue ->
				val enabled = newValue as Boolean
				fragment.settings.isDevTabBackgroundEnabled = enabled
				DevTabBackground.updateVisibleMainActivities(context)
				true
			}
		}
		screen.addPreference(switch)
	}
}
