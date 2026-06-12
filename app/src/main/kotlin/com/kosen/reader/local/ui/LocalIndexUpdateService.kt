package com.kosen.reader.local.ui

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.core.ui.CoroutineIntentService
import com.kosen.reader.local.data.index.LocalMangaIndex
import javax.inject.Inject

@AndroidEntryPoint
class LocalIndexUpdateService : CoroutineIntentService() {

	@Inject
	lateinit var localMangaIndex: LocalMangaIndex

	override suspend fun IntentJobContext.processIntent(intent: Intent) {
		localMangaIndex.update()
	}

	override fun IntentJobContext.onError(error: Throwable) = Unit
}
