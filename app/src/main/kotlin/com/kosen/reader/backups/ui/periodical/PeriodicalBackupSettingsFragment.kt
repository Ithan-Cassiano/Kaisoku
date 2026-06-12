package com.kosen.reader.backups.ui.periodical

import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.inputmethod.EditorInfo
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.fragment.app.viewModels
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.os.OpenDocumentTreeHelper
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BasePreferenceFragment
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.tryLaunch
import com.kosen.reader.settings.utils.EditTextBindListener
import com.kosen.reader.settings.utils.EditTextFallbackSummaryProvider
import com.kosen.reader.settings.utils.PasswordSummaryProvider
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class PeriodicalBackupSettingsFragment : BasePreferenceFragment(R.string.periodic_backups),
	ActivityResultCallback<Uri?>,
	SharedPreferences.OnSharedPreferenceChangeListener {

	@Inject
	lateinit var telegramBackupUploader: TelegramBackupUploader

	private val viewModel by viewModels<PeriodicalBackupSettingsViewModel>()

	private val outputSelectCall = OpenDocumentTreeHelper(this, this)

	private var pendingInitialUri: Uri? = null

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_backup_periodic)
		findPreference<PreferenceCategory>(AppSettings.KEY_BACKUP_TG)?.isVisible = true
		findPreference<EditTextPreference>(AppSettings.KEY_BACKUP_TG_TOKEN)?.let { pref ->
			@Suppress("UsePropertyAccessSyntax")
			pref.setOnBindEditTextListener(
				EditTextBindListener(
					inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
					hint = null,
					validator = null,
				),
			)
			pref.summaryProvider = Preference.SummaryProvider<EditTextPreference> { preference ->
				if (preference.text.isNullOrBlank()) {
					getString(R.string.telegram_bot_token_summary)
				} else {
					PasswordSummaryProvider().provideSummary(preference)
				}
			}
		}
		findPreference<EditTextPreference>(AppSettings.KEY_BACKUP_TG_CHAT)?.summaryProvider =
			EditTextFallbackSummaryProvider(R.string.telegram_chat_id_summary)
		updateTelegramPreferences()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		settings.subscribe(this)
		viewModel.lastBackupDate.observe(viewLifecycleOwner, ::bindLastBackupInfo)
		viewModel.backupsDirectory.observe(viewLifecycleOwner, ::bindOutputSummary)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
		viewModel.isTelegramCheckLoading.observe(viewLifecycleOwner) {
			updateTelegramPreferences()
		}
	}

	override fun onDestroyView() {
		settings.unsubscribe(this)
		super.onDestroyView()
	}

	override fun onPreferenceTreeClick(preference: Preference): Boolean {
		val result = when (preference.key) {
			AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT_DRIVE -> {
				pendingInitialUri = googleDriveInitialUri()
				outputSelectCall.tryLaunch(pendingInitialUri)
			}

			AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT_LOCAL -> {
				pendingInitialUri = null
				outputSelectCall.tryLaunch(null)
			}

			AppSettings.KEY_BACKUP_TG_OPEN -> {
				telegramBackupUploader.openBotInApp(router)
				true
			}

			AppSettings.KEY_BACKUP_TG_TEST -> {
				viewModel.checkTelegram()
				true
			}

			else -> return super.onPreferenceTreeClick(preference)
		}
		if (result == false) {
			Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
		return true
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			context?.contentResolver?.takePersistableUriPermission(result, takeFlags)
			settings.periodicalBackupDirectory = result
			settings.isPeriodicalBackupEnabled = true
			viewModel.updateSummaryData()
			requireContext().startService(Intent(requireContext(), PeriodicalBackupService::class.java))
			Snackbar.make(listView, R.string.backup_periodic_destination_set, Snackbar.LENGTH_LONG).show()
		}
		pendingInitialUri = null
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		when (key) {
			AppSettings.KEY_BACKUP_TG_TOKEN,
			AppSettings.KEY_BACKUP_TG_ENABLED,
			AppSettings.KEY_BACKUP_TG_CHAT,
			AppSettings.KEY_BACKUP_PERIODICAL_ENABLED,
			-> updateTelegramPreferences()
		}
	}

	private fun bindOutputSummary(path: String?) {
		val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_OUTPUT) ?: return
		preference.summary = when {
			path.isNullOrEmpty() -> getString(R.string.backups_output_directory_summary)
			else -> getString(R.string.backup_periodic_current_folder, path.substringAfterLast('/'))
		}
	}

	private fun bindLastBackupInfo(lastBackupDate: Date?) {
		val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_LAST) ?: return
		preference.summary = lastBackupDate?.let {
			preference.context.getString(
				R.string.last_successful_backup,
				DateUtils.getRelativeTimeSpanString(it.time),
			)
		}
		preference.isVisible = lastBackupDate != null
	}

	private fun updateTelegramPreferences() {
		findPreference<Preference>(AppSettings.KEY_BACKUP_TG_OPEN)?.isVisible = settings.backupTelegramBotToken == null
		findPreference<Preference>(AppSettings.KEY_BACKUP_TG_TEST)?.isVisible = viewModel.isTelegramAvailable
		findPreference<Preference>(AppSettings.KEY_BACKUP_TG_TEST)?.isEnabled = !viewModel.isTelegramCheckLoading.value
	}

	private fun googleDriveInitialUri(): Uri? = runCatching {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Uri.parse("content://com.google.android.apps.docs.storage/document/root")
		} else {
			null
		}
	}.getOrNull()
}
