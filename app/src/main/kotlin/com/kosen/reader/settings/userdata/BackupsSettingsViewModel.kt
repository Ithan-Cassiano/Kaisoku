package com.kosen.reader.settings.userdata

import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.history.domain.PrivateHistoryAdminUseCase
import javax.inject.Inject

@HiltViewModel
class BackupsSettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val privateHistoryAdminUseCase: PrivateHistoryAdminUseCase,
) : BaseViewModel() {

    val onPrivateHistoryExported = MutableEventFlow<Int>()
    val onPrivateHistoryCleared = MutableEventFlow<Int>()

    val isHiddenHistoryModeEnabled = settings.observeAsFlow(
        key = AppSettings.KEY_INCOGNITO_MODE_TYPE,
        valueProducer = { incognitoMode == IncognitoMode.HIDDEN_HISTORY },
    )

    val periodicalBackupFrequency = settings.observeAsFlow(
        key = AppSettings.KEY_BACKUP_PERIODICAL_ENABLED,
        valueProducer = { isPeriodicalBackupEnabled },
    ).flatMapLatest { isEnabled ->
        if (isEnabled) {
            settings.observeAsFlow(
                key = AppSettings.KEY_BACKUP_PERIODICAL_FREQUENCY,
                valueProducer = { periodicalBackupFrequency },
            )
        } else {
            flowOf(0)
        }
    }

    fun exportPrivateHistory(uri: Uri) {
        launchLoadingJob(Dispatchers.IO) {
            val count = privateHistoryAdminUseCase.exportPrivateHistory(uri)
            onPrivateHistoryExported.call(count)
        }
    }

    fun clearPrivateHistory() {
        launchJob(Dispatchers.Default) {
            val count = privateHistoryAdminUseCase.clearPrivateHistory()
            onPrivateHistoryCleared.call(count)
        }
    }
}
