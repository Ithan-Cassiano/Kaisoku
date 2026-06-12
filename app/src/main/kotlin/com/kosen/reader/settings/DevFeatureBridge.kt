package com.kosen.reader.settings

import com.kosen.reader.BuildConfig
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.databinding.SheetWelcomeBinding
import com.kosen.reader.main.ui.MainActivity

internal object DevFeatureBridge {

	fun bindAppearanceSettings(fragment: AppearanceSettingsFragment) {
		if (!BuildConfig.DEBUG) {
			return
		}
		invokeDebugUnit("com.kosen.reader.settings.DevAppearanceSettings", "bind", fragment)
	}

	fun bindWelcomeBackgroundOption(
		binding: SheetWelcomeBinding,
		settings: AppSettings,
		mainActivity: MainActivity?,
	) {
		if (!BuildConfig.DEBUG) {
			return
		}
		runCatching {
			Class.forName("com.kosen.reader.main.ui.welcome.DevWelcomeBackgroundOption")
				.getMethod(
					"bind",
					SheetWelcomeBinding::class.java,
					AppSettings::class.java,
					MainActivity::class.java,
				)
				.invoke(null, binding, settings, mainActivity)
		}.onFailure { it.printStackTrace() }
	}

	private fun invokeDebugUnit(className: String, methodName: String, arg: Any) {
		runCatching {
			Class.forName(className)
				.getMethod(methodName, arg.javaClass)
				.invoke(null, arg)
		}.onFailure { it.printStackTrace() }
	}
}
