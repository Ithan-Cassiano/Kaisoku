package com.kosen.reader.main.ui.welcome

import androidx.core.view.isVisible
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.databinding.SheetWelcomeBinding
import com.kosen.reader.main.ui.DevTabBackground
import com.kosen.reader.main.ui.MainActivity

object DevWelcomeBackgroundOption {

	fun bind(
		binding: SheetWelcomeBinding,
		settings: AppSettings,
		mainActivity: MainActivity?,
	) {
		binding.layoutDevTabBackground.isVisible = true
		binding.switchDevTabBackground.isChecked = settings.isDevTabBackgroundEnabled
		binding.switchDevTabBackground.setOnCheckedChangeListener { _, isChecked ->
			settings.isDevTabBackgroundEnabled = isChecked
			mainActivity?.let { DevTabBackground.update(it) }
		}
	}
}
