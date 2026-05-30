package org.koitharu.kotatsu.settings.sources.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.browser.BaseBrowserActivity
import org.koitharu.kotatsu.browser.BrowserCallback
import org.koitharu.kotatsu.browser.BrowserClient
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.parser.setAuthSessionConfirmed
import org.koitharu.kotatsu.core.util.ext.getDisplayMessage
import org.koitharu.kotatsu.parsers.MangaParserAuthProvider
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable

@AndroidEntryPoint
class SourceAuthActivity : BaseBrowserActivity(), BrowserCallback {

	private lateinit var authProvider: MangaParserAuthProvider

	private var parserRepository: ParserMangaRepository? = null

	private var authCheckJob: Job? = null

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: ParserMangaRepository?) {
		if (repository == null) {
			finishAfterTransition()
			return
		}
		parserRepository = repository
		authProvider = repository.getAuthProvider() ?: run {
			Toast.makeText(
				this,
				getString(R.string.auth_not_supported_by, source.getTitle(this)),
				Toast.LENGTH_SHORT,
			).show()
			finishAfterTransition()
			return
		}
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = true)
		viewBinding.webView.webViewClient = BrowserClient(this, adBlock)
		lifecycleScope.launch {
			try {
				proxyProvider.applyWebViewConfig()
			} catch (e: Exception) {
				Snackbar.make(viewBinding.webView, e.getDisplayMessage(resources), Snackbar.LENGTH_LONG).show()
			}
			if (savedInstanceState == null) {
				val url = authProvider.authUrl
				onTitleChanged(
					source.getTitle(this@SourceAuthActivity),
					getString(R.string.loading_),
				)
				viewBinding.webView.loadUrl(url)
			}
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.opt_source_auth, menu)
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
		R.id.action_confirm_login -> {
			confirmAuthAndFinish()
			true
		}
		android.R.id.home -> {
			viewBinding.webView.stopLoading()
			setResult(RESULT_CANCELED)
			finishAfterTransition()
			true
		}
		else -> super.onOptionsItemSelected(item)
	}

	override fun onLoadingStateChanged(isLoading: Boolean) {
		super.onLoadingStateChanged(isLoading)
		if (isLoading) {
			return
		}
		val prevJob = authCheckJob
		authCheckJob = lifecycleScope.launch {
			prevJob?.join()
			val isAuthorized = withContext(Dispatchers.IO) {
				CookieManager.getInstance().flush()
				delay(400)
				var ok = runCatchingCancellable {
					authProvider.isAuthorized()
				}.getOrDefault(false)
				if (!ok) {
					delay(800)
					CookieManager.getInstance().flush()
					ok = runCatchingCancellable {
						authProvider.isAuthorized()
					}.getOrDefault(false)
				}
				if (ok) {
					CookieManager.getInstance().flush()
				}
				ok
			}
			if (isAuthorized) {
				finishAuthSuccess()
			}
		}
	}

	private fun confirmAuthAndFinish() {
		lifecycleScope.launch {
			withContext(Dispatchers.IO) {
				CookieManager.getInstance().flush()
				parserRepository?.setAuthSessionConfirmed(true)
			}
			finishAuthSuccess()
		}
	}

	private fun finishAuthSuccess() {
		Toast.makeText(this, R.string.auth_complete, Toast.LENGTH_SHORT).show()
		setResult(RESULT_OK)
		finishAfterTransition()
	}

	class Contract : ActivityResultContract<MangaSource, Boolean>() {

		override fun createIntent(context: Context, input: MangaSource) = AppRouter.sourceAuthIntent(context, input)

		override fun parseResult(resultCode: Int, intent: Intent?) = resultCode == RESULT_OK
	}

	companion object {
		const val TAG = "SourceAuthActivity"
	}
}
