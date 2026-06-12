package com.kosen.reader.browser

import android.os.Bundle
import android.view.View
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.core.model.MangaSource
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.network.CommonHeaders
import com.kosen.reader.core.network.proxy.ProxyProvider
import com.kosen.reader.core.network.webview.adblock.AdBlock
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.parser.ParserMangaRepository
import com.kosen.reader.core.parser.PluginMangaRepository
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.util.ext.configureForParser
import com.kosen.reader.core.util.ext.consumeAll
import com.kosen.reader.databinding.ActivityBrowserBinding
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.util.nullIfEmpty
import javax.inject.Inject

@AndroidEntryPoint
abstract class BaseBrowserActivity : BaseActivity<ActivityBrowserBinding>(), BrowserCallback {

	@Inject
	lateinit var proxyProvider: ProxyProvider

	@Inject
	lateinit var mangaRepositoryFactory: MangaRepository.Factory

	@Inject
	lateinit var adBlock: AdBlock

	private lateinit var onBackPressedCallback: WebViewBackPressedCallback

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (!setContentViewWebViewSafe { ActivityBrowserBinding.inflate(layoutInflater) }) {
			return
		}
		viewBinding.webView.webChromeClient = ProgressChromeClient(viewBinding.progressBar)
		onBackPressedCallback = WebViewBackPressedCallback(viewBinding.webView)
		onBackPressedDispatcher.addCallback(onBackPressedCallback)

		val mangaSource = MangaSource(intent?.getStringExtra(AppRouter.KEY_SOURCE))
		val mangaRepository = mangaRepositoryFactory.create(mangaSource)
		val userAgent = intent?.getStringExtra(AppRouter.KEY_USER_AGENT)?.nullIfEmpty()
			?: when (mangaRepository) {
				is ParserMangaRepository -> mangaRepository.getRequestHeaders()[CommonHeaders.USER_AGENT]
				is PluginMangaRepository -> mangaRepository.getRequestHeaders()[CommonHeaders.USER_AGENT]
				else -> null
			}
		viewBinding.webView.configureForParser(userAgent)

		onCreate2(savedInstanceState, mangaSource, mangaRepository)
	}

	protected abstract fun onCreate2(
		savedInstanceState: Bundle?,
		source: MangaSource,
		repository: MangaRepository?,
	)

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val type = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
		val barsInsets = insets.getInsets(type)
		viewBinding.webView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		return insets.consumeAll(type)
	}

	override fun onPause() {
		viewBinding.webView.onPause()
		super.onPause()
	}

	override fun onResume() {
		super.onResume()
		viewBinding.webView.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()
		if (hasViewBinding()) {
			viewBinding.webView.stopLoading()
			viewBinding.webView.destroy()
		}
	}

	override fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.progressBar.isVisible = isLoading
	}

	override fun onTitleChanged(title: CharSequence, subtitle: CharSequence?) {
		this.title = title
		supportActionBar?.subtitle = subtitle
	}

	override fun onHistoryChanged() {
		onBackPressedCallback.onHistoryChanged()
	}
}
