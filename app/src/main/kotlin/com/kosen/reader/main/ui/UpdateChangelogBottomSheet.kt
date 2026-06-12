package com.kosen.reader.main.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import com.kosen.reader.BuildConfig
import com.kosen.reader.R
import com.kosen.reader.core.github.AppVersion
import com.kosen.reader.core.github.UPDATE_CHANNEL_DEV_MARKER
import com.kosen.reader.core.github.UPDATE_CHANNEL_RELEASE_MARKER
import com.kosen.reader.core.nav.router
import com.kosen.reader.databinding.SheetUpdateChangelogBinding

class UpdateChangelogBottomSheet : AppCompatDialogFragment() {

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val version = requireArguments().getParcelable<AppVersion>(ARG_VERSION)
			?: return super.onCreateDialog(savedInstanceState)
		val binding = SheetUpdateChangelogBinding.inflate(LayoutInflater.from(requireContext()))
		binding.textTitle.text = getString(R.string.new_version_s, version.name)
		binding.textVersionBadge.text = if (BuildConfig.DEBUG) {
			getString(R.string.update_channel_dev)
		} else {
			getString(R.string.update_channel_release)
		}
		val cleaned = cleanReleaseNotes(version.description)
		val markwon = Markwon.create(requireContext())
		if (cleaned.isBlank()) {
			binding.textChangelog.setText(R.string.app_update_available)
		} else {
			markwon.setMarkdown(binding.textChangelog, cleaned)
		}
		binding.buttonUpdate.setOnClickListener {
			router.openAppUpdate()
			dismiss()
		}
		binding.buttonLater.setOnClickListener { dismiss() }
		return MaterialAlertDialogBuilder(requireContext())
			.setView(binding.root)
			.create()
	}

	override fun onStart() {
		super.onStart()
		val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
		dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
	}

	private fun cleanReleaseNotes(raw: String): String = raw.lines()
		.filterNot { line ->
			val t = line.trim()
			t.startsWith("[versionCode:", ignoreCase = true) ||
				t.startsWith("[channel:", ignoreCase = true) ||
				t.equals(UPDATE_CHANNEL_DEV_MARKER, ignoreCase = true) ||
				t.equals(UPDATE_CHANNEL_RELEASE_MARKER, ignoreCase = true)
		}
		.joinToString("\n")
		.trim()

	companion object {
		private const val ARG_VERSION = "version"

		fun newInstance(version: AppVersion) = UpdateChangelogBottomSheet().apply {
			arguments = Bundle(1).apply {
				putParcelable(ARG_VERSION, version)
			}
		}
	}
}
