package com.kosen.reader.browser

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.core.network.webview.adblock.AdBlock
import com.kosen.reader.core.ui.CoroutineIntentService
import javax.inject.Inject

@AndroidEntryPoint
class AdListUpdateService : CoroutineIntentService() {

	@Inject
	lateinit var updater: AdBlock.Updater

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		updater.updateList()
	}

	override fun IntentJobContext.onError(error: Throwable) = Unit
}
