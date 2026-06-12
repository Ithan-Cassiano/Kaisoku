package com.kosen.reader.settings.about

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import com.kosen.reader.BuildConfig
import com.kosen.reader.R
import com.kosen.reader.core.github.ApkUpdateCompatibility
import com.kosen.reader.core.github.ApkUpdateCompatibilityChecker
import com.kosen.reader.core.github.GithubUpdateAuth
import com.kosen.reader.core.github.AppUpdateRepository
import com.kosen.reader.core.network.BaseHttpClient
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.core.util.ext.requireValue
import com.kosen.reader.parsers.util.await
import com.kosen.reader.parsers.util.runCatchingCancellable
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val MIME_APK = "application/vnd.android.package-archive"

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
	private val repository: AppUpdateRepository,
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext private val context: Context,
	private val settings: com.kosen.reader.core.prefs.AppSettings,
) : BaseViewModel() {

	val nextVersion = repository.observeAvailableUpdate()
	val downloadProgress = MutableStateFlow(-1f)
	val downloadState = MutableStateFlow(DownloadManager.STATUS_PENDING)
	val installIntent = MutableStateFlow<Intent?>(null)
	val onDownloadDone = MutableEventFlow<Intent>()
	val onInstallPermissionRequired = MutableEventFlow<Unit>()
	val onSignatureMismatch = MutableEventFlow<Unit>()
	val onVersionDowngrade = MutableEventFlow<Unit>()
	val onPackageMigration = MutableEventFlow<Unit>()

	private var downloadedApkFile: File? = null

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
		launchLoadingJob(Dispatchers.IO) {
			installIntent.value = null
			val version = nextVersion.requireValue()
			val fileName = "kosen-update-${version.name}.apk".replace('/', '_')
			val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
				?: throw IOException("Download directory unavailable")
			val destFile = File(destDir, fileName)
			downloadProgress.value = 0f
			downloadState.value = DownloadManager.STATUS_RUNNING
			runCatchingCancellable {
				if (!GithubUpdateAuth.isConfigured(settings)) {
					throw IOException(
						context.getString(
							if (BuildConfig.DEBUG) {
								R.string.dev_update_token_missing
							} else {
								R.string.release_update_token_missing
							},
						),
					)
				}
				val request = GithubUpdateAuth.downloadRequest(version.apkUrl, settings)
					.header("User-Agent", "Kosen/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT})")
					.get()
					.build()
				okHttp.newCall(request).await().use { response ->
					if (!response.isSuccessful) {
						throw IOException("HTTP ${response.code}")
					}
					val body = response.body ?: throw IOException("Empty response")
					val totalBytes = body.contentLength()
					destFile.outputStream().use { output ->
						body.byteStream().use { input ->
							val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
							var downloaded = 0L
							while (true) {
								val read = input.read(buffer)
								if (read <= 0) {
									break
								}
								output.write(buffer, 0, read)
								downloaded += read
								if (totalBytes > 0L) {
									downloadProgress.value = downloaded.toFloat() / totalBytes
								}
							}
						}
					}
					if (!destFile.isFile || destFile.length() <= 0L) {
						throw IOException("Downloaded file is empty")
					}
				}
				downloadProgress.value = 1f
				downloadState.value = DownloadManager.STATUS_SUCCESSFUL
				prepareInstall(destFile)
			}.onFailure { e ->
				destFile.delete()
				downloadState.value = DownloadManager.STATUS_FAILED
				downloadProgress.value = -1f
				errorEvent.call(e)
			}
		}
	}

	fun retryInstall() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
			onInstallPermissionRequired.call(Unit)
			return
		}
		installIntent.value?.let { onDownloadDone.call(it) }
			?: downloadedApkFile?.let { prepareInstall(it) }
	}

	private fun prepareInstall(apkFile: File) {
		if (!apkFile.isFile || !apkFile.canRead()) {
			errorEvent.call(IllegalStateException(context.getString(R.string.error_occurred)))
			return
		}
		when (ApkUpdateCompatibilityChecker.check(context, apkFile)) {
			ApkUpdateCompatibility.SIGNATURE_MISMATCH -> {
				downloadedApkFile = apkFile
				onSignatureMismatch.call(Unit)
				return
			}
			ApkUpdateCompatibility.VERSION_DOWNGRADE -> {
				downloadedApkFile = apkFile
				onVersionDowngrade.call(Unit)
				return
			}
			ApkUpdateCompatibility.PACKAGE_MIGRATION -> {
				downloadedApkFile = apkFile
				onPackageMigration.call(Unit)
				launchInstallIntent(apkFile)
				return
			}
			ApkUpdateCompatibility.INVALID_APK -> {
				errorEvent.call(IllegalStateException(context.getString(R.string.error_occurred)))
				return
			}
			ApkUpdateCompatibility.COMPATIBLE -> Unit
		}
		downloadedApkFile = apkFile
		launchInstallIntent(apkFile)
	}

	private fun launchInstallIntent(apkFile: File) {
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

	fun tryInstallAfterUninstall() {
		val apkFile = downloadedApkFile ?: return
		if (!isAppInstalled()) {
			prepareInstall(apkFile)
		}
	}

	private fun isAppInstalled(): Boolean = try {
		context.packageManager.getPackageInfo(context.packageName, 0)
		true
	} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
		false
	}
}
