package com.kosen.reader.settings.sources.auth

import android.webkit.CookieManager
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal object SourceAuthWebViewVerifier {

	private const val DOM_SCRIPT = """
		(function() {
			try {
				var now = Date.now();
				var ttl = 7 * 24 * 60 * 60 * 1000;
				var data = { consentAt: now, expiresAt: now + ttl };
				localStorage.setItem('age_gate_consent', JSON.stringify(data));
				document.documentElement.classList.add('age-gate-accepted');
				var gate = document.getElementById('ageGate');
				if (gate) gate.style.display = 'none';
			} catch (e) {}
			if (document.querySelector(
				'a[href*="sair"], a[href*="logout"], a[href*="perfil"], a[href*="minha-conta"], ' +
				'.user-menu, .nav-user, [data-user-name], .user-avatar, .header-user'
			)) {
				return 'true';
			}
			var logins = document.querySelectorAll('#toggle-login, a.login, a[href="/entrar"]');
			for (var i = 0; i < logins.length; i++) {
				var login = logins[i];
				var style = window.getComputedStyle(login);
				if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
				var rect = login.getBoundingClientRect();
				if (rect.width >= 1 && rect.height >= 1) return 'false';
			}
			return 'unknown';
		})();
	"""

	suspend fun isLoggedIn(webView: WebView, fetchProbeUrl: String? = null): Boolean {
		CookieManager.getInstance().flush()
		val domResult = evaluateDom(webView)
		if (domResult == true) {
			return true
		}
		if (fetchProbeUrl != null) {
			evaluateFetchProbe(webView, fetchProbeUrl)?.let { return it }
		}
		return domResult == true
	}

	private suspend fun evaluateDom(webView: WebView): Boolean? = suspendCancellableCoroutine { cont ->
		webView.evaluateJavascript(DOM_SCRIPT) { result ->
			cont.resume(
				when (result?.trim()?.trim('"')) {
					"true" -> true
					"false" -> false
					else -> null
				},
			)
		}
	}

	private suspend fun evaluateFetchProbe(webView: WebView, probeUrl: String): Boolean? =
		suspendCancellableCoroutine { cont ->
			val script = """
				(function() {
					return fetch('$probeUrl', { credentials: 'include', redirect: 'follow' })
						.then(function(response) { return response.text(); })
						.then(function(html) {
							var locked = 0;
							var readable = 0;
							var re = /chapter-link-wrap[^>]*onclick="([^"]*)"/g;
							var match;
							while ((match = re.exec(html)) !== null) {
								if (match[1].indexOf('showLoginModal') >= 0) {
									locked++;
								} else if (match[1].indexOf('location.href') >= 0) {
									readable++;
								}
							}
							if (locked > 0) return false;
							if (readable > 0) return true;
							return null;
						})
						.catch(function() { return null; });
				})();
			""".trimIndent()
			webView.evaluateJavascript(script) { result ->
				cont.resume(
					when (result?.trim()?.trim('"')) {
						"true" -> true
						"false" -> false
						else -> null
					},
				)
			}
		}
}
