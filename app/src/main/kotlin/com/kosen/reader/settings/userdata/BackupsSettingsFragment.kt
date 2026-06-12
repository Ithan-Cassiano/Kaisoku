package com.kosen.reader.settings.userdata



import android.app.Activity

import android.content.SharedPreferences

import android.net.Uri

import android.os.Bundle

import android.view.View

import androidx.activity.result.ActivityResultCallback

import androidx.activity.result.contract.ActivityResultContracts

import androidx.fragment.app.viewModels

import androidx.lifecycle.Lifecycle

import androidx.preference.Preference

import androidx.preference.PreferenceCategory

import androidx.preference.SwitchPreferenceCompat

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import com.google.android.material.snackbar.Snackbar

import dagger.hilt.android.AndroidEntryPoint

import com.kosen.reader.R

import com.kosen.reader.backups.domain.BackupUtils

import com.kosen.reader.backups.ui.backup.BackupService

import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver

import com.kosen.reader.core.nav.router

import com.kosen.reader.core.prefs.AppSettings

import com.kosen.reader.core.ui.BasePreferenceFragment

import com.kosen.reader.core.ui.dialog.buildAlertDialog

import com.kosen.reader.core.util.ext.observe

import com.kosen.reader.core.util.ext.observeEvent

import com.kosen.reader.core.util.ext.tryLaunch

import com.kosen.reader.history.ui.PrivateHistoryPinSetupActivity

import com.kosen.reader.history.ui.PrivateHistoryUnlockActivity



@AndroidEntryPoint

class BackupsSettingsFragment : BasePreferenceFragment(R.string.backup_restore),

    ActivityResultCallback<Uri?>,

    SharedPreferences.OnSharedPreferenceChangeListener,

    Preference.OnPreferenceChangeListener {



    private val viewModel: BackupsSettingsViewModel by viewModels()



    private val backupSelectCall = registerForActivityResult(

        ActivityResultContracts.OpenDocument(),

        this,

    )



    private val backupCreateCall = registerForActivityResult(

        ActivityResultContracts.CreateDocument("application/zip"),

    ) { uri ->

        if (uri != null && !BackupService.start(requireContext(), uri)) {

            Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()

        }

    }



    private val exportPrivateHistoryCall = registerForActivityResult(

        ActivityResultContracts.CreateDocument("application/json"),

    ) { uri: Uri? ->

        if (uri != null) {

            viewModel.exportPrivateHistory(uri)

        }

    }



    private val pinSetupCall = registerForActivityResult(

        ActivityResultContracts.StartActivityForResult(),

    ) { result ->

        val lockPref = findPreference<SwitchPreferenceCompat>(AppSettings.KEY_PRIVATE_HISTORY_LOCK)

        if (result.resultCode == Activity.RESULT_OK) {

            lockPref?.isChecked = true

            bindPrivateHistoryLockState()

        } else {

            lockPref?.isChecked = false

            settings.isPrivateHistoryLockEnabled = false

        }

    }



    private val verifyPinForChangeCall = registerForActivityResult(

        ActivityResultContracts.StartActivityForResult(),

    ) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            pinSetupCall.launch(PrivateHistoryPinSetupActivity.newIntent(requireContext()))

        }

    }



    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        addPreferencesFromResource(R.xml.pref_backups)

    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        settings.subscribe(this)

        bindPeriodicalBackupSummary()

        bindPrivateHistoryLockState()

        findPreference<SwitchPreferenceCompat>(AppSettings.KEY_PRIVATE_HISTORY_VOLUME_SHORTCUT)?.run {

            isChecked = settings.isPrivateHistoryVolumeShortcutEnabled

            onPreferenceChangeListener = this@BackupsSettingsFragment

        }

        viewModel.isHiddenHistoryModeEnabled.observe(viewLifecycleOwner, Lifecycle.State.STARTED) { enabled ->

            findPreference<Preference>(AppSettings.KEY_PRIVATE_HISTORY_EXPORT)?.isEnabled = enabled

            findPreference<Preference>(AppSettings.KEY_PRIVATE_HISTORY_CLEAR)?.isEnabled = enabled

            findPreference<PreferenceCategory>(AppSettings.KEY_PRIVATE_HISTORY_CATEGORY)?.summary =

                if (enabled) null else getString(R.string.private_history_backup_requires_mode)

            findPreference<SwitchPreferenceCompat>(AppSettings.KEY_PRIVATE_HISTORY_VOLUME_SHORTCUT)?.isEnabled =

                enabled

        }

        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))

        viewModel.onPrivateHistoryExported.observeEvent(viewLifecycleOwner) { count ->

            val message = if (count > 0) {

                getString(R.string.private_history_export_done, count)

            } else {

                getString(R.string.private_history_export_empty)

            }

            Snackbar.make(listView, message, Snackbar.LENGTH_SHORT).show()

        }

        viewModel.onPrivateHistoryCleared.observeEvent(viewLifecycleOwner) { count ->

            Snackbar.make(

                listView,

                getString(R.string.private_history_clear_done, count),

                Snackbar.LENGTH_SHORT,

            ).show()

        }

    }



    override fun onDestroyView() {

        settings.unsubscribe(this)

        super.onDestroyView()

    }



    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {

        if (key == AppSettings.KEY_PRIVATE_HISTORY_LOCK) {

            bindPrivateHistoryLockState()

        }

    }



    override fun onPreferenceTreeClick(preference: Preference): Boolean {

        return when (preference.key) {

            AppSettings.KEY_BACKUP -> {

                if (!backupCreateCall.tryLaunch(BackupUtils.generateFileName(preference.context))) {

                    Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()

                }

                true

            }



            AppSettings.KEY_RESTORE -> {

                if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {

                    Snackbar.make(listView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()

                }

                true

            }



            AppSettings.KEY_PRIVATE_HISTORY_CHANGE_PIN -> {

                verifyPinForChangeCall.launch(

                    PrivateHistoryUnlockActivity.newVerifyIntent(requireContext()),

                )

                true

            }



            AppSettings.KEY_PRIVATE_HISTORY_EXPORT -> {

                exportPrivateHistoryCall.launch("kosen-private-history.json")

                true

            }



            AppSettings.KEY_PRIVATE_HISTORY_CLEAR -> {

                confirmClearPrivateHistory()

                true

            }



            else -> super.onPreferenceTreeClick(preference)

        }

    }



    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {

        when (preference.key) {

            AppSettings.KEY_PRIVATE_HISTORY_LOCK -> {

                if (newValue == true && !settings.hasPrivateHistoryPin()) {

                    pinSetupCall.launch(PrivateHistoryPinSetupActivity.newIntent(requireContext()))

                    return false

                }

            }

            AppSettings.KEY_PRIVATE_HISTORY_VOLUME_SHORTCUT -> {

                if (newValue == true) {

                    showVolumeShortcutInstructions {

                        settings.isPrivateHistoryVolumeShortcutEnabled = true

                        findPreference<SwitchPreferenceCompat>(preference.key)?.isChecked = true

                    }

                    return false

                }

                settings.isPrivateHistoryVolumeShortcutEnabled = false

                return true

            }

        }

        return true

    }



    override fun onActivityResult(result: Uri?) {

        if (result != null) {

            router.showBackupRestoreDialog(result)

        }

    }



    private fun showVolumeShortcutInstructions(onConfirm: () -> Unit) {

        MaterialAlertDialogBuilder(requireContext())

            .setTitle(R.string.private_history_volume_shortcut)

            .setMessage(R.string.private_history_volume_shortcut_instructions)

            .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }

            .setNegativeButton(android.R.string.cancel, null)

            .show()

    }



    private fun confirmClearPrivateHistory() {

        buildAlertDialog(requireContext(), isCentered = true) {

            setTitle(R.string.private_history_clear)

            setMessage(R.string.private_history_clear_confirm)

            setNegativeButton(android.R.string.cancel, null)

            setPositiveButton(R.string.clear) { _, _ ->

                buildAlertDialog(requireContext(), isCentered = true) {

                    setMessage(R.string.private_history_clear_confirm_again)

                    setNegativeButton(android.R.string.cancel, null)

                    setPositiveButton(R.string.clear) { _, _ ->

                        viewModel.clearPrivateHistory()

                    }

                }.show()

            }

        }.show()

    }



    private fun bindPeriodicalBackupSummary() {

        val preference = findPreference<Preference>(AppSettings.KEY_BACKUP_PERIODICAL_ENABLED) ?: return

        val entries = resources.getStringArray(R.array.backup_frequency)

        val entryValues = resources.getStringArray(R.array.values_backup_frequency)

        viewModel.periodicalBackupFrequency.observe(viewLifecycleOwner) { freq ->

            preference.summary = if (freq == 0L) {

                getString(R.string.periodic_backups_summary)

            } else {

                val index = entryValues.indexOf(freq.toString())

                entries.getOrNull(index)?.let { getString(R.string.periodic_backups_enabled_summary, it) }

                    ?: getString(R.string.periodic_backups_summary)

            }

        }

    }



    private fun bindPrivateHistoryLockState() {

        val lockPref = findPreference<SwitchPreferenceCompat>(AppSettings.KEY_PRIVATE_HISTORY_LOCK)

        lockPref?.isChecked = settings.isPrivateHistoryLockEnabled && settings.hasPrivateHistoryPin()

        lockPref?.onPreferenceChangeListener = this

        findPreference<Preference>(AppSettings.KEY_PRIVATE_HISTORY_CHANGE_PIN)?.isVisible =

            settings.hasPrivateHistoryPin()

    }

}

