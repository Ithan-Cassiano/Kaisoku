package com.kosen.reader.core.network.webview

import android.content.Context
import android.util.AndroidRuntimeException
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.kosen.reader.core.exceptions.CloudFlareException
import com.kosen.reader.core.network.CommonHeaders
import com.kosen.reader.core.network.cookies.MutableCookieJar
import com.kosen.reader.core.network.proxy.ProxyProvider
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.parser.ParserMangaRepository
import com.kosen.reader.core.util.ext.configureForParser
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.sanitizeHeaderValue
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.network.UserAgents
import com.kosen.reader.parsers.util.nullIfEmpty
import com.kosen.reader.parsers.util.runCatchingCancellable
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.ranges.contains

@Singleton
class WebViewExecutor @Inject constructor(
	@ApplicationContext private val context: Context,
	private val proxyProvider: ProxyProvider,
	private val cookieJar: MutableCookieJar,
	private val mangaRepositoryFactoryProvider: Provider<MangaRepository.Factory>,
) {

	private var webViewCached: WeakReference<WebView>? = null
	private val mutex = Mutex()

	val defaultUserAgent: String? by lazy {
		try {
			WebSettings.getDefaultUserAgent(context)
		} catch (e: AndroidRuntimeException) {
			e.printStackTraceDebug()
			// Probably WebView is not available
			null
		}
	}

    suspend fun evaluateJs(
        baseUrl: String?,
        script: String,
        timeoutMs: Long = 15000L,
        preserveCookies: Boolean = false,
        userAgent: String? = null,
    ): String? = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            val webView = obtainWebView()
            userAgent?.let { webView.settings.userAgentString = it }
            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            try {
                if (baseUrl.isNullOrEmpty()) {
                    return@withContext suspendCoroutine { cont ->
                        webView.evaluateJavascript(script) { cont.resume(it.takeUnless { r -> r == "null" }) }
                    }
                }

                val baseUri = android.net.Uri.parse(baseUrl)
                val originalHost = baseUri.host

                suspendCoroutine { continuation ->
                    var hasResumed = false
                    val scriptStarted = java.util.concurrent.atomic.AtomicBoolean(false)

                    val resumeOnce: (String?) -> Unit = { result ->
                        if (!hasResumed) {
                            hasResumed = true
                            handler.removeCallbacksAndMessages(null)
                            webView.stopLoading()
                            continuation.resume(result)
                        }
                    }

                    fun runMainScript() {
                        if (!scriptStarted.compareAndSet(false, true)) {
                            return
                        }
                        webView.evaluateJavascript(script) { result ->
                            if (hasResumed) return@evaluateJavascript
                            val content = result?.takeUnless { it == "null" }
                            if (!content.isNullOrBlank()) {
                                resumeOnce(content)
                            }
                        }
                    }

                    val resultPollScript = RESULT_POLL_SCRIPT

                    val contentPoller = object : Runnable {
                        val startTime = System.currentTimeMillis()
                        override fun run() {
                            if (hasResumed) return
                            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                                return
                            }
                            webView.evaluateJavascript(resultPollScript) { result ->
                                if (hasResumed) return@evaluateJavascript
                                val content = result?.takeUnless { it == "null" }
                                if (!content.isNullOrBlank()) {
                                    resumeOnce(content)
                                } else {
                                    handler.postDelayed(this, 500)
                                }
                            }
                        }
                    }

                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return false
                            val requestHost = url.host
                            if (originalHost != null && requestHost != null && requestHost.contains(originalHost)) {
                                return false
                            }
                            println("DEBUG: Blocked redirect to external domain: $url")
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (hasResumed || url == "about:blank") return
                            runMainScript()
                        }
                    }

                    val headers = buildMap {
                        put("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                        webView.settings.userAgentString?.takeIf { it.isNotBlank() }?.let {
                            put("User-Agent", it)
                        }
                    }
                    CookieManager.getInstance().flush()
                    CookieManager.getInstance().flush()
                    if (preserveCookies) {
                        webView.loadDataWithBaseURL(baseUrl, " ", "text/html", null, null)
                    } else {
                        webView.loadUrl(baseUrl, headers)
                    }

                    handler.postDelayed(contentPoller, 1500)

                    handler.postDelayed({
                        if (!hasResumed) {
                            resumeOnce(null)
                        }
                    }, timeoutMs)
                }
            } finally {
                webView.stopLoading()
            }
        }
    }

    private companion object {
        const val RESULT_POLL_SCRIPT =
            "(function(){ return window.__evaluateJsDone !== undefined ? window.__evaluateJsDone : null; })()"
    }

    suspend fun tryResolveCaptcha(exception: CloudFlareException, timeout: Long): Boolean = mutex.withLock {
		runCatchingCancellable {
			withContext(Dispatchers.Main.immediate) {
				val webView = obtainWebView()
				try {
					exception.source.getUserAgent()?.let {
						webView.settings.userAgentString = it
					}
					withTimeout(timeout) {
						suspendCancellableCoroutine { cont ->
							webView.webViewClient = CaptchaContinuationClient(
								cookieJar = cookieJar,
								targetUrl = exception.url,
								continuation = cont,
							)
							webView.loadUrl(exception.url)
						}
					}
				} finally {
					webView.reset()
				}
			}
		}.onFailure { e ->
			exception.addSuppressed(e)
			e.printStackTraceDebug()
		}.isSuccess
	}

    @MainThread
    private fun obtainWebView(): WebView = webViewCached?.get() ?: WebView(context).also {
        it.configureForParser(UserAgents.CHROME_MOBILE)
        webViewCached = WeakReference(it)
    }

	private fun MangaSource.getUserAgent(): String? {
		val repository = mangaRepositoryFactoryProvider.get().create(this) as? ParserMangaRepository
		return repository?.getRequestHeaders()?.get(CommonHeaders.USER_AGENT)
	}

    @MainThread
    fun getDefaultUserAgentSync() = runCatching {
        obtainWebView().settings.userAgentString.sanitizeHeaderValue().trim().nullIfEmpty()
    }.onFailure { e ->
        e.printStackTraceDebug()
    }.getOrNull()

	@MainThread
	private fun WebView.reset() {
		stopLoading()
		webViewClient = WebViewClient()
		settings.userAgentString = defaultUserAgent
		loadDataWithBaseURL(null, " ", "text/html", null, null)
		clearHistory()
	}
}
