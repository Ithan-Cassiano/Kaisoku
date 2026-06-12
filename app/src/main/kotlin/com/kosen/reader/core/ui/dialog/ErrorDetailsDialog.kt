package com.kosen.reader.core.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.crash.CrashReportFormatter
import com.kosen.reader.core.github.AppUpdateRepository
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.AlertDialogFragment
import com.kosen.reader.core.util.ext.copyToClipboard
import com.kosen.reader.core.util.ext.getCauseUrl
import com.kosen.reader.core.util.ext.isHttpUrl
import com.kosen.reader.core.util.ext.isReportable
import com.kosen.reader.core.util.ext.requireSerializable
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.databinding.DialogErrorDetailsBinding
import javax.inject.Inject

@AndroidEntryPoint
class ErrorDetailsDialog : AlertDialogFragment<DialogErrorDetailsBinding>(), View.OnClickListener {

	private lateinit var exception: Throwable

	@Inject
	lateinit var appUpdateRepository: AppUpdateRepository

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val args = requireArguments()
		exception = args.requireSerializable(AppRouter.KEY_ERROR)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): DialogErrorDetailsBinding {
		return DialogErrorDetailsBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: DialogErrorDetailsBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.buttonBrowser.setOnClickListener(this)
		binding.textViewSummary.text = exception.message
		val isUrlAvailable = exception.getCauseUrl()?.isHttpUrl() == true
		binding.buttonBrowser.isVisible = isUrlAvailable
		binding.textViewBrowser.isVisible = isUrlAvailable
		binding.textViewDescription.setTextAndVisible(
			if (appUpdateRepository.isUpdateAvailable) {
				R.string.error_disclaimer_app_outdated
			} else if (exception.isReportable()) {
				R.string.error_disclaimer_report
			} else {
				0
			},
		)
	}

	@Suppress("NAME_SHADOWING")
	override fun onBuildDialog(builder: MaterialAlertDialogBuilder): MaterialAlertDialogBuilder {
		val builder = super.onBuildDialog(builder)
			.setCancelable(true)
			.setNegativeButton(R.string.close, null)
			.setTitle(R.string.error_details)
			.setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
				context?.let {
					val report = CrashReportFormatter.build(it, exception, Thread.currentThread().name)
					it.copyToClipboard(getString(R.string.error), report.body)
				}
			}
		if (appUpdateRepository.isUpdateAvailable) {
			builder.setPositiveButton(R.string.update) { _, _ ->
				router.openAppUpdate()
				dismiss()
			}
		} else if (exception.isReportable()) {
			builder.setPositiveButton(R.string.report) { _, _ ->
				val report = context?.let {
					CrashReportFormatter.build(it, exception, Thread.currentThread().name)
				}
				context?.copyToClipboard(getString(R.string.error), report?.body ?: exception.stackTraceToString())
				router.openExternalBrowser(report?.issueUrl ?: getString(R.string.url_error_report), getString(R.string.report))
				dismiss()
			}
		}
		return builder
	}

	override fun onClick(v: View) {
		router.openBrowser(
			url = exception.getCauseUrl() ?: return,
			source = null,
			title = null,
		)
	}
}
