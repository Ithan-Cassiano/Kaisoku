package org.koitharu.kotatsu.main.ui.protect

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.ScreenshotsPolicy
import org.koitharu.kotatsu.core.prefs.observeAsFlow
import org.koitharu.kotatsu.core.ui.DefaultActivityLifecycleCallbacks
import java.lang.ref.WeakReference
import javax.inject.Inject

class ScreenshotPolicyHelper @Inject constructor(
	private val settings: AppSettings,
) : DefaultActivityLifecycleCallbacks {

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		(activity as? ContentContainer)?.setupScreenshotPolicy(activity)
	}

	private fun ContentContainer.setupScreenshotPolicy(activity: Activity) {
		val activityRef = WeakReference(activity)
		val contentRef = WeakReference(this)
		lifecycleScope.launch {
			settings.observeAsFlow(AppSettings.KEY_SCREENSHOTS_POLICY) { screenshotsPolicy }
				.flatMapLatest { policy ->
					when (policy) {
						ScreenshotsPolicy.ALLOW -> flowOf(false)
						ScreenshotsPolicy.BLOCK_NSFW -> contentRef.get()?.isNsfwContent()?.distinctUntilChanged() ?: flowOf(false)
						ScreenshotsPolicy.BLOCK_ALL -> flowOf(true)
						ScreenshotsPolicy.BLOCK_INCOGNITO -> settings.observeAsFlow(AppSettings.KEY_INCOGNITO_MODE) {
							isIncognitoModeEnabled
						}
					}
				}.distinctUntilChanged()
				.collect { isSecure ->
					val window = activityRef.get()?.window ?: return@collect
					if (isSecure) {
						window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
					} else {
						window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
					}
				}
		}
	}

	interface ContentContainer : LifecycleOwner {

		@MainThread
		fun isNsfwContent(): Flow<Boolean>
	}
}
