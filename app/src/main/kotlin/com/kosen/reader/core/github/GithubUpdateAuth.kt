package com.kosen.reader.core.github

import okhttp3.Request
import com.kosen.reader.BuildConfig
import com.kosen.reader.core.prefs.AppSettings

object GithubUpdateAuth {

	private val isDevChannel: Boolean
		get() = BuildConfig.DEBUG

	fun resolveToken(settings: AppSettings? = null): String? {
		if (isDevChannel) {
			return BuildConfig.DEV_GITHUB_TOKEN.takeIf { it.isNotBlank() }
				?: settings?.devGithubToken
		}
		return BuildConfig.RELEASE_GITHUB_TOKEN.takeIf { it.isNotBlank() }
	}

	fun isConfigured(settings: AppSettings? = null): Boolean =
		!resolveToken(settings).isNullOrBlank()

	fun useApiAssetUrl(settings: AppSettings? = null): Boolean =
		isConfigured(settings)

	fun applyApiAuth(builder: Request.Builder, settings: AppSettings? = null): Request.Builder = builder.apply {
		resolveToken(settings)?.let { header("Authorization", "Bearer $it") }
		header("Accept", "application/vnd.github+json")
		header("X-GitHub-Api-Version", "2022-11-28")
	}

	fun downloadRequest(url: String, settings: AppSettings? = null): Request.Builder = Request.Builder()
		.url(url)
		.apply {
			resolveToken(settings)?.let { header("Authorization", "Bearer $it") }
			if (useApiAssetUrl(settings)) {
				header("Accept", "application/octet-stream")
			}
		}
}
