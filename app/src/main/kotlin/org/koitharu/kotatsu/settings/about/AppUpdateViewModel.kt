package org.koitharu.kotatsu.settings.about

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.github.AppUpdateRepository
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.core.util.ext.requireValue
import java.io.File
import javax.inject.Inject

private const val MIME_APK = "application/vnd.android.package-archive"

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
	private val repository: AppUpdateRepository,
	@ApplicationContext private val context: Context,
) : BaseViewModel() {

	val nextVersion = repository.observeAvailableUpdate()
	val downloadProgress = MutableStateFlow(-1f)
	val downloadState = MutableStateFlow(DownloadManager.STATUS_PENDING)
	val installIntent = MutableStateFlow<Intent?>(null)
	val onDownloadDone = MutableEventFlow<Intent>()
	val onInstallPermissionRequired = MutableEventFlow<Unit>()

	private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
	private val appName = context.getString(R.string.app_name)
	private var pendingDownloadId: Long? = null

	init {
		if (nextVersion.value == null) {
			launchLoadingJob(Dispatchers.Default) {
				repository.fetchUpdate()
			}
		}
	}

	fun startDownload() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
			onInstallPermissionRequired.call(Unit)
			return
		}
		launchLoadingJob(Dispatchers.Default) {
			installIntent.value = null
			val version = nextVersion.requireValue()
			val url = version.apkUrl.toUri()
			val fileName = "kosen-update-${version.name}.apk".replace('/', '_')
			val request = DownloadManager.Request(url)
				.setTitle("$appName v${version.name}")
				.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
				.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
				.setMimeType(MIME_APK)
				.setAllowedOverMetered(true)
			val downloadId = downloadManager.enqueue(request)
			pendingDownloadId = downloadId
			observeDownload(downloadId)
		}
	}

	fun onDownloadComplete(intent: Intent) {
		val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L)
		if (downloadId == 0L) {
			return
		}
		prepareInstall(downloadId)
	}

	fun retryInstall() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
			onInstallPermissionRequired.call(Unit)
			return
		}
		installIntent.value?.let { onDownloadDone.call(it) } ?: pendingDownloadId?.let { prepareInstall(it) }
	}

	private fun prepareInstall(downloadId: Long) {
		launchLoadingJob(Dispatchers.Default) {
			val apkFile = getDownloadedApkFile(downloadId)
			if (apkFile == null) {
				errorEvent.call(IllegalStateException(context.getString(R.string.error_occurred)))
				return@launchLoadingJob
			}
			val uri = FileProvider.getUriForFile(
				context,
				"${BuildConfig.APPLICATION_ID}.files",
				apkFile,
			)
			val installerIntent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(uri, MIME_APK)
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			installIntent.value = installerIntent
			onDownloadDone.call(installerIntent)
		}
	}

	private fun getDownloadedApkFile(downloadId: Long): File? {
		val query = DownloadManager.Query().setFilterById(downloadId)
		downloadManager.query(query).use { cursor ->
			if (!cursor.moveToFirst()) {
				return null
			}
			val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
			if (status != DownloadManager.STATUS_SUCCESSFUL) {
				return null
			}
			val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
				?: return null
			val path = localUri.toUri().path ?: return null
			return File(path).takeIf { it.isFile && it.canRead() }
		}
	}

	private suspend fun observeDownload(id: Long) {
		val query = DownloadManager.Query()
		query.setFilterById(id)
		while (currentCoroutineContext().isActive) {
			downloadManager.query(query).use { cursor ->
				if (cursor.moveToFirst()) {
					val bytesDownloaded = cursor.getLong(
						cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
					)
					val bytesTotal = cursor.getLong(
						cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
					)
					downloadProgress.value = bytesDownloaded.toFloat() / bytesTotal
					val state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
					downloadState.value = state
					if (state == DownloadManager.STATUS_SUCCESSFUL) {
						prepareInstall(id)
						return
					}
					if (state == DownloadManager.STATUS_FAILED) {
						return
					}
				}
			}
			delay(100)
		}
	}
}
