package com.kosen.reader.download.ui.worker

import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.FlowCollector
import com.kosen.reader.R
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.util.ext.findActivity
import com.kosen.reader.main.ui.owners.BottomNavOwner

class DownloadStartedObserver(
	private val snackbarHost: View,
) : FlowCollector<Unit> {

	override suspend fun emit(value: Unit) {
		val snackbar = Snackbar.make(snackbarHost, R.string.download_started, Snackbar.LENGTH_LONG)
		(snackbarHost.context.findActivity() as? BottomNavOwner)?.let {
			snackbar.anchorView = it.bottomNav
		}
		val router = AppRouter.from(snackbarHost)
		if (router != null) {
			snackbar.setAction(R.string.details) { router.openDownloads() }
		}
		snackbar.show()
	}
}
