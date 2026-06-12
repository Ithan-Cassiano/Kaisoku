package com.kosen.reader.settings.about

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.text.buildSpannedString
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.kosen.reader.R
import com.kosen.reader.BuildConfig
import com.kosen.reader.core.github.AppVersion
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.util.FileSize
import com.kosen.reader.core.util.ext.consumeAllSystemBarsInsets
import com.kosen.reader.core.util.ext.getDisplayMessage
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.core.util.ext.showOrHide
import com.kosen.reader.core.util.ext.systemBarsInsets
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.ActivityAppUpdateBinding

@AndroidEntryPoint
class AppUpdateActivity : BaseActivity<ActivityAppUpdateBinding>(), View.OnClickListener {

	private val viewModel: AppUpdateViewModel by viewModels()

	private val installPermissionRequest = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
			viewModel.startDownload()
		} else {
			Snackbar.make(viewBinding.scrollView, R.string.allow_install_unknown_apps, Snackbar.LENGTH_LONG).show()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityAppUpdateBinding.inflate(layoutInflater))
		viewModel.nextVersion.observe(this, ::onNextVersionChanged)
		viewBinding.buttonCancel.setOnClickListener(this)
		viewBinding.buttonUpdate.setOnClickListener(this)

		combine(viewModel.isLoading, viewModel.downloadProgress, ::Pair)
			.observe(this, ::onProgressChanged)
		viewModel.downloadState.observe(this, ::onDownloadStateChanged)
		viewModel.onError.observeEvent(this, ::onError)
		viewModel.onInstallPermissionRequired.observeEvent(this) {
			requestInstallPermission()
		}
		viewModel.onSignatureMismatch.observeEvent(this) {
			showSignatureMismatchDialog()
		}
		viewModel.onVersionDowngrade.observeEvent(this) {
			showVersionDowngradeDialog()
		}
		viewModel.onPackageMigration.observeEvent(this) {
			showPackageMigrationDialog()
		}
		viewModel.onDownloadDone.observeEvent(this) { intent ->
			try {
				startActivity(intent)
			} catch (e: ActivityNotFoundException) {
				viewModel.installIntent.value = null
				onError(e)
			}
		}
	}

	override fun onResume() {
		super.onResume()
		viewModel.tryInstallAfterUninstall()
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.root.updatePadding(top = barsInsets.top)
		viewBinding.dockedToolbarChild.updateLayoutParams<MarginLayoutParams> {
			leftMargin = barsInsets.left
			rightMargin = barsInsets.right
			bottomMargin = barsInsets.bottom
		}
		viewBinding.scrollView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_cancel -> finishAfterTransition()
			R.id.button_update -> doUpdate()
		}
	}

	private suspend fun onNextVersionChanged(version: AppVersion?) {
		viewBinding.buttonUpdate.isEnabled = version != null && !viewModel.isLoading.value
		if (version == null) {
			viewBinding.textViewContent.setText(R.string.loading_)
			return
		}
		val markwon = Markwon.create(this)
		val message = withContext(Dispatchers.Default) {
			buildSpannedString {
				append(getString(R.string.new_version_s, version.name))
				appendLine()
				append(getString(R.string.size_s, FileSize.BYTES.format(this@AppUpdateActivity, version.apkSize)))
				appendLine()
				appendLine()
				append(markwon.toMarkdown(version.description))
			}
		}
		markwon.setParsedMarkdown(viewBinding.textViewContent, message)
	}

	private fun doUpdate() {
		if (viewModel.installIntent.value != null) {
			viewModel.retryInstall()
			return
		}
		viewModel.startDownload()
	}

	private fun requestInstallPermission() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
			viewModel.startDownload()
			return
		}
		val intent = Intent(
			Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
			Uri.parse("package:$packageName"),
		)
		installPermissionRequest.launch(intent)
	}

	private fun showPackageMigrationDialog() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.update_package_migration_title)
			.setMessage(R.string.update_package_migration_message)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}

	private fun showSignatureMismatchDialog() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.update_signature_conflict_title)
			.setMessage(R.string.update_signature_conflict_message)
			.setPositiveButton(R.string.update_uninstall_app) { _, _ ->
				startActivity(
					Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
				)
			}
			.setNeutralButton(R.string.open_in_browser) { _, _ -> openInBrowser() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
		viewBinding.textViewError.setText(R.string.update_retry_after_uninstall)
		viewBinding.textViewError.isVisible = true
	}

	private fun showVersionDowngradeDialog() {
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.update_version_downgrade_title)
			.setMessage(R.string.update_version_downgrade_message)
			.setPositiveButton(R.string.update_uninstall_app) { _, _ ->
				startActivity(
					Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
				)
			}
			.setNeutralButton(R.string.open_in_browser) { _, _ -> openInBrowser() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
		viewBinding.textViewError.setText(R.string.update_retry_after_uninstall)
		viewBinding.textViewError.isVisible = true
	}

	private fun openInBrowser() {
		if (BuildConfig.DEBUG) {
			Snackbar.make(viewBinding.scrollView, R.string.dev_update_private_channel, Snackbar.LENGTH_LONG).show()
			return
		}
		val latestVersion = viewModel.nextVersion.value ?: return
		if (!router.openExternalBrowser(latestVersion.apkUrl, getString(R.string.open_in_browser))) {
			Snackbar.make(viewBinding.scrollView, R.string.operation_not_supported, Snackbar.LENGTH_SHORT).show()
		}
	}

	private fun onProgressChanged(value: Pair<Boolean, Float>) {
		val (isLoading, downloadProgress) = value
		val indicator = viewBinding.progressBar
		indicator.showOrHide(isLoading)
		indicator.isIndeterminate = downloadProgress <= 0f
		if (downloadProgress > 0f) {
			indicator.setProgressCompat((indicator.max * downloadProgress).toInt(), true)
		}
		viewBinding.buttonUpdate.isEnabled = !isLoading && viewModel.nextVersion.value != null
	}

	private fun onDownloadStateChanged(state: Int) {
		val message = when (state) {
			DownloadManager.STATUS_FAILED -> R.string.error_occurred
			DownloadManager.STATUS_PAUSED -> R.string.downloads_paused
			else -> 0
		}
		viewBinding.textViewError.setTextAndVisible(message)
		if (state == DownloadManager.STATUS_FAILED) {
			Snackbar.make(viewBinding.scrollView, R.string.open_in_browser, Snackbar.LENGTH_LONG)
				.setAction(R.string.open_in_browser) { openInBrowser() }
				.show()
		}
	}

	private fun onError(e: Throwable) {
		viewBinding.textViewError.textAndVisible = e.getDisplayMessage(resources)
		Snackbar.make(viewBinding.scrollView, R.string.open_in_browser, Snackbar.LENGTH_LONG)
			.setAction(R.string.open_in_browser) { openInBrowser() }
			.show()
	}
}
