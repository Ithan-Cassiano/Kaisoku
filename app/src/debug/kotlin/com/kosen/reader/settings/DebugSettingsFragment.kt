package com.kosen.reader.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import leakcanary.LeakCanary
import com.kosen.reader.KosenApp
import com.kosen.reader.R
import com.kosen.reader.core.model.TestMangaSource
import com.kosen.reader.core.nav.router
import android.content.Intent
import com.kosen.reader.explore.ui.DevSourceHealthActivity
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BasePreferenceFragment
import com.kosen.reader.settings.utils.SplitSwitchPreference
import org.koitharu.workinspector.WorkInspector

@AndroidEntryPoint
class DebugSettingsFragment : BasePreferenceFragment(R.string.debug), Preference.OnPreferenceChangeListener,
	Preference.OnPreferenceClickListener {

	private val application
		get() = requireContext().applicationContext as KosenApp

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_debug)
		findPreference<SplitSwitchPreference>(KEY_LEAK_CANARY)?.let { pref ->
			pref.isChecked = application.isLeakCanaryEnabled
			pref.onPreferenceChangeListener = this
			pref.onContainerClickListener = this
		}
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_NSFW_FILTER_STRICT)?.let { pref ->
			pref.isChecked = settings.isNsfwFilterStrict
			pref.onPreferenceChangeListener = this
		}
		findPreference<androidx.preference.EditTextPreference>(AppSettings.KEY_DEV_GITHUB_TOKEN)?.let { pref ->
			pref.text = settings.devGithubToken
			pref.setOnPreferenceChangeListener { _, newValue ->
				settings.devGithubToken = newValue?.toString()
				true
			}
		}
	}

	override fun onResume() {
		super.onResume()
		findPreference<SplitSwitchPreference>(KEY_LEAK_CANARY)?.isChecked = application.isLeakCanaryEnabled
		findPreference<SwitchPreferenceCompat>(AppSettings.KEY_NSFW_FILTER_STRICT)?.isChecked =
			settings.isNsfwFilterStrict
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean = when (preference.key) {
		KEY_WORK_INSPECTOR -> {
			startActivity(WorkInspector.getIntent(preference.context))
			true
		}

		KEY_TEST_PARSER -> {
			router.openList(TestMangaSource, null, null)
			true
		}

		KEY_SOURCE_HEALTH -> {
			startActivity(Intent(requireContext(), DevSourceHealthActivity::class.java))
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onPreferenceClick(preference: Preference): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			startActivity(LeakCanary.newLeakDisplayActivityIntent())
			true
		}

		else -> super.onPreferenceTreeClick(preference)
	}

	override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean = when (preference.key) {
		KEY_LEAK_CANARY -> {
			application.isLeakCanaryEnabled = newValue as Boolean
			true
		}

		AppSettings.KEY_NSFW_FILTER_STRICT -> {
			settings.isNsfwFilterStrict = newValue as Boolean
			true
		}

		else -> false
	}

	private companion object {

		const val KEY_LEAK_CANARY = "leak_canary"
		const val KEY_WORK_INSPECTOR = "work_inspector"
		const val KEY_TEST_PARSER = "test_parser"
		const val KEY_SOURCE_HEALTH = "source_health"
	}
}
