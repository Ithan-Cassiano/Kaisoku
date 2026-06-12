package com.kosen.reader.backups.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.kosen.reader.backups.data.BackupRepository
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.core.util.ext.require
import com.kosen.reader.core.util.progress.Progress
import java.util.zip.Deflater
import java.util.zip.ZipOutputStream
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val repository: BackupRepository,
	@ApplicationContext context: Context,
) : BaseViewModel() {

	val progress = MutableStateFlow(Progress.INDETERMINATE)
	val onBackupDone = MutableEventFlow<Uri>()

	private val destination = savedStateHandle.require<Uri>(AppRouter.KEY_DATA)
	private val contentResolver: ContentResolver = context.contentResolver

	init {
		launchLoadingJob(Dispatchers.Default) {
			ZipOutputStream(checkNotNull(contentResolver.openOutputStream(destination))).use {
				it.setLevel(Deflater.BEST_COMPRESSION)
				repository.createBackup(it, progress)
			}
			onBackupDone.call(destination)
		}
	}
}
