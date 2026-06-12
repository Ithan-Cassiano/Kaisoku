package com.kosen.reader.settings.sources.auth

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kosen.reader.R
import com.kosen.reader.browser.BaseBrowserActivity
import com.kosen.reader.browser.BrowserCallback
import com.kosen.reader.browser.BrowserClient
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.util.ext.getDisplayMessage
import com.kosen.reader.parsers.MangaParserAuthProvider
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.settings.sources.auth.SourceAuthWebViewVerifier

@AndroidEntryPoint
class SourceAuthActivity : BaseBrowserActivity(), BrowserCallback {

	private lateinit var authProvider: MangaParserAuthProvider

	private var authRepository: SourceAuthRepository? = null

	override fun onCreate2(savedInstanceState: Bundle?, source: MangaSource, repository: MangaRepository?) {
		val authHost = repository?.asSourceAuthRepository()
		if (authHost == null) {
			finishAfterTransition()
			return
		}
		authRepository = authHost
		authProvider = authHost.getAuthProvider() ?: run {
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
	}

	private fun confirmAuthAndFinish() {
		lifecycleScope.launch {
			val webViewOk = withContext(Dispatchers.Main) {
				runCatchingCancellable {
					SourceAuthWebViewVerifier.isLoggedIn(viewBinding.webView, authProvider.authVerifyUrl)
				}.getOrDefault(false)
			}
			val isAuthorized = webViewOk || withContext(Dispatchers.IO) {
				CookieManager.getInstance().flush()
				delay(400)
				runCatchingCancellable {
					authProvider.isAuthorized()
				}.getOrDefault(false)
			}
			if (!isAuthorized) {
				Snackbar.make(
					viewBinding.webView,
					R.string.auth_not_confirmed,
					Snackbar.LENGTH_LONG,
				).show()
				return@launch
			}
			finishAuthSuccess()
		}
	}

	private fun finishAuthSuccess() {
		lifecycleScope.launch {
			withContext(Dispatchers.IO) {
				authRepository?.setAuthSessionConfirmed(true)
			}
			Toast.makeText(this@SourceAuthActivity, R.string.auth_complete, Toast.LENGTH_SHORT).show()
			setResult(RESULT_OK)
			finishAfterTransition()
		}
	}

	class Contract : ActivityResultContract<MangaSource, Boolean>() {

		override fun createIntent(context: Context, input: MangaSource) = AppRouter.sourceAuthIntent(context, input)

		override fun parseResult(resultCode: Int, intent: Intent?) = resultCode == RESULT_OK
	}

	companion object {
		const val TAG = "SourceAuthActivity"
	}
}
