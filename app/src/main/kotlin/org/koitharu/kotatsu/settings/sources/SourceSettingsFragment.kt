package org.koitharu.kotatsu.settings.sources

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.parser.EmptyMangaRepository
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.SourceSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.withArgs
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.settings.sources.auth.SourceAuthActivity

@AndroidEntryPoint
class SourceSettingsFragment : BasePreferenceFragment(0), Preference.OnPreferenceChangeListener {

	private val viewModel: SourceSettingsViewModel by viewModels()

	private val sourceAuthLauncher = registerForActivityResult(SourceAuthActivity.Contract()) {
		viewModel.onResume()
	}

	override fun onResume() {
		super.onResume()
		context?.let { ctx ->
			setTitle(viewModel.source.getTitle(ctx))
		}
		viewModel.onResume()
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		preferenceManager.sharedPreferencesName = SourceSettings.prefsName(viewModel.source)
		addPreferencesFromResource(R.xml.pref_source)
		addPreferencesFromRepository(viewModel.repository)
		val isValidSource = viewModel.repository !is EmptyMangaRepository

		findPreference<SwitchPreferenceCompat>(KEY_ENABLE)?.run {
			isVisible = isValidSource && !settings.isAllSourcesEnabled
			onPreferenceChangeListener = this@SourceSettingsFragment
		}
		findPreference<Preference>(KEY_AUTH)?.run {
			val authProvider = (viewModel.repository as? ParserMangaRepository)?.getAuthProvider()
			isVisible = authProvider != null
		}
		findPreference<Preference>(KEY_AUTH_STATUS)?.run {
			isVisible = (viewModel.repository as? ParserMangaRepository)?.getAuthProvider() != null
		}
		findPreference<Preference>(SourceSettings.KEY_SLOWDOWN)?.isVisible = isValidSource
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.isAuthorized.filterNotNull().observe(viewLifecycleOwner) { isAuthorized ->
			updateAuthUi(isAuthorized, viewModel.username.value)
		}
		viewModel.username.observe(viewLifecycleOwner) { username ->
			updateAuthUi(viewModel.isAuthorized.value == true, username)
		}
		viewModel.onError.observeEvent(
			viewLifecycleOwner,
			SnackbarErrorObserver(
				listView,
				this,
				exceptionResolver,
			) { viewModel.onResume() },
		)
		viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
			findPreference<Preference>(KEY_AUTH)?.isEnabled = !isLoading
		}
		viewModel.isEnabled.observe(viewLifecycleOwner) { enabled ->
			findPreference<SwitchPreferenceCompat>(KEY_ENABLE)?.isChecked = enabled
		}
		viewModel.browserUrl.observe(viewLifecycleOwner) {
			findPreference<Preference>(AppSettings.KEY_OPEN_BROWSER)?.run {
				isVisible = it != null
				summary = it
			}
		}
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(listView))
	}

	private fun updateAuthUi(isAuthorized: Boolean, username: String?) {
		findPreference<Preference>(KEY_AUTH_STATUS)?.apply {
			summary = if (isAuthorized) {
				getString(R.string.source_auth_status_connected)
			} else {
				getString(R.string.source_auth_status_disconnected)
			}
		}
		findPreference<Preference>(KEY_AUTH)?.apply {
			isEnabled = true
			title = if (isAuthorized) {
				getString(R.string.source_account_connected)
			} else {
				getString(R.string.sign_in)
			}
			summary = when {
				isAuthorized && !username.isNullOrBlank() -> getString(R.string.logged_in_as, username)
				isAuthorized -> getString(R.string.source_account_connected_summary)
				else -> getString(R.string.source_login_required_summary)
			}
		}
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		return when (preference.key) {
			KEY_AUTH -> {
				sourceAuthLauncher.launch(viewModel.source)
				true
			}

			AppSettings.KEY_OPEN_BROWSER -> {
				router.openBrowser(
					url = viewModel.browserUrl.value ?: return false,
					source = viewModel.source,
					title = viewModel.source.getTitle(preference.context),
				)
				true
			}

			AppSettings.KEY_COOKIES_CLEAR -> {
				viewModel.clearCookies()
				true
			}

			else -> super.onPreferenceTreeClick(preference)
		}
	}

	override fun onDisplayPreferenceDialog(preference: Preference) {
		if (preference.key == SourceSettings.KEY_DOMAIN) {
			if (parentFragmentManager.findFragmentByTag(DomainDialogFragment.DIALOG_FRAGMENT_TAG) != null) {
				return
			}
			val f = DomainDialogFragment.newInstance(preference.key)
			@Suppress("DEPRECATION")
			f.setTargetFragment(this, 0)
			f.show(parentFragmentManager, DomainDialogFragment.DIALOG_FRAGMENT_TAG)
			return
		}
		super.onDisplayPreferenceDialog(preference)
	}

	override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
		when (preference.key) {
			KEY_ENABLE -> viewModel.setEnabled(newValue == true)
			else -> return false
		}
		return true
	}

	class DomainDialogFragment : EditTextPreferenceDialogFragmentCompat() {

		override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
			super.onPrepareDialogBuilder(builder)
			builder.setNeutralButton(R.string.reset) { _, _ ->
				resetValue()
			}
		}

		private fun resetValue() {
			val editTextPreference = preference as EditTextPreference
			if (editTextPreference.callChangeListener("")) {
				editTextPreference.text = ""
			}
		}

		companion object {

			const val DIALOG_FRAGMENT_TAG: String = "androidx.preference.PreferenceFragment.DIALOG"

			fun newInstance(key: String) = DomainDialogFragment().withArgs(1) {
				putString(ARG_KEY, key)
			}
		}
	}

	companion object {

		private const val KEY_AUTH = "auth"
		private const val KEY_AUTH_STATUS = "auth_status"
		private const val KEY_ENABLE = "enable"

		fun newInstance(source: MangaSource) = SourceSettingsFragment().withArgs(1) {
			putString(AppRouter.KEY_SOURCE, source.name)
		}
	}
}
