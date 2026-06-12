package com.kosen.reader.core.github

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import com.kosen.reader.BuildConfig
import com.kosen.reader.R
import com.kosen.reader.core.network.BaseHttpClient
import com.kosen.reader.core.os.AppValidator
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.util.ext.asArrayList
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.parsers.util.await
import com.kosen.reader.parsers.util.json.mapJSONNotNull
import com.kosen.reader.parsers.util.parseJsonArray
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.parsers.util.suspendlazy.getOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val CONTENT_TYPE_APK = "application/vnd.android.package-archive"
private const val RELEASES_PER_PAGE = 100
private const val MAX_RELEASE_PAGES = 5

@Singleton
class AppUpdateRepository @Inject constructor(
	private val appValidator: AppValidator,
	private val settings: AppSettings,
	@BaseHttpClient private val okHttp: OkHttpClient,
	@ApplicationContext private val context: Context,
) {

	private val availableUpdate = MutableStateFlow<AppVersion?>(null)
	private val updateCheckMessage = MutableStateFlow<String?>(null)
	private val isDevUpdateChannel: Boolean
		get() = BuildConfig.DEBUG
	private val releasesUrlBase = buildString {
		append("https://api.github.com/repos/")
		append(context.getString(R.string.github_updates_repo))
		append("/releases")
	}

	val isUpdateAvailable: Boolean
		get() = availableUpdate.value != null

	fun observeAvailableUpdate() = availableUpdate.asStateFlow()

	fun observeUpdateCheckMessage() = updateCheckMessage.asStateFlow()

	fun consumeUpdateCheckMessage(): String? {
		val message = updateCheckMessage.value
		updateCheckMessage.value = null
		return message
	}

	suspend fun getAvailableVersions(): List<AppVersion> {
		if (!GithubUpdateAuth.isConfigured(settings)) {
			android.util.Log.w(
				"UPDATE_DEBUG",
				if (isDevUpdateChannel) {
					"Dev update token missing (kosen.dev_github_token in local.properties)"
				} else {
					"Release update token missing (kosen.release_github_token in local.properties)"
				},
			)
			return emptyList()
		}
		android.util.Log.d("UPDATE_DEBUG", "=== Getting available versions from: $releasesUrlBase ===")
		val requestBuilder = Request.Builder().get()
		GithubUpdateAuth.applyApiAuth(requestBuilder, settings)
		val jsonArray = fetchAllReleasePages(requestBuilder)

		return jsonArray.mapJSONNotNull { json ->
			val releaseName = json.getString("name")
			val releaseTag = json.getString("tag_name")
			android.util.Log.d("UPDATE_DEBUG", "Processing release: '$releaseName' (tag: '$releaseTag')")

			val assets = json.optJSONArray("assets")
			android.util.Log.d("UPDATE_DEBUG", "  Assets found: ${assets?.length() ?: 0}")

			if (assets != null) {
				for (i in 0 until assets.length()) {
					val assetObj = assets.getJSONObject(i)
					val assetName = assetObj.optString("name", "unknown")
					val contentType = assetObj.optString("content_type", "unknown")
					android.util.Log.d("UPDATE_DEBUG", "    Asset $i: '$assetName' (content_type: '$contentType')")
				}
			}

			val asset = assets?.find { jo ->
				val contentType = jo.optString("content_type")
				val name = jo.optString("name")
				val isApk = contentType == CONTENT_TYPE_APK ||
					(contentType == "application/octet-stream" && name.endsWith(".apk", ignoreCase = true)) ||
					name.endsWith(".apk", ignoreCase = true)
				if (!isApk) {
					return@find false
				}
				if (isDevUpdateChannel) {
					name.contains("debug", ignoreCase = true)
				} else {
					!name.contains("debug", ignoreCase = true) &&
						!name.contains("nightly", ignoreCase = true) &&
						!name.contains("-Dev-", ignoreCase = true)
				}
			}

			if (asset == null) {
				android.util.Log.d("UPDATE_DEBUG", "  No valid APK asset found for release '$releaseName'")
				return@mapJSONNotNull null
			}

			val description = json.getString("body")
			val apkDownloadUrl = if (GithubUpdateAuth.useApiAssetUrl(settings)) {
				asset.getString("url")
			} else {
				asset.getString("browser_download_url")
			}
			val versionName = releaseTag.removePrefix("v")
			val version = AppVersion(
				id = json.getLong("id"),
				url = json.getString("html_url"),
				name = versionName,
				apkSize = asset.getLong("size"),
				apkUrl = apkDownloadUrl,
				description = description,
				versionCode = resolveUpdateVersionCode(versionName, description, isDevUpdateChannel),
			)
			if (!isDevUpdateChannel && !version.isReleaseChannelUpdate()) {
				android.util.Log.d("UPDATE_DEBUG", "  Skipping non-release channel release '$releaseName'")
				return@mapJSONNotNull null
			}
			if (isDevUpdateChannel && version.description.contains(UPDATE_CHANNEL_RELEASE_MARKER, ignoreCase = true)) {
				android.util.Log.d("UPDATE_DEBUG", "  Skipping release-channel release '$releaseName' on dev track")
				return@mapJSONNotNull null
			}

			android.util.Log.d(
				"UPDATE_DEBUG",
				"  Creating AppVersion: name='${version.name}' (from tag='$releaseTag'), versionId=${VersionId(version.name)}",
			)
			version
		}
	}

	suspend fun fetchUpdate(): AppVersion? = withContext(Dispatchers.Default) {
		android.util.Log.d("UPDATE_DEBUG", "=== Starting fetchUpdate ===")

		if (!isUpdateSupported()) {
			android.util.Log.d("UPDATE_DEBUG", "Update not supported, returning null")
			return@withContext null
		}
		android.util.Log.d("UPDATE_DEBUG", "Update is supported, proceeding...")

		runCatchingCancellable {
			val normalizedVersionName = normalizeUpdateVersionName(BuildConfig.VERSION_NAME)
			val currentVersion = VersionId(normalizedVersionName)
			val currentVersionCode = BuildConfig.VERSION_CODE
			android.util.Log.d(
				"UPDATE_DEBUG",
				"Current version: ${BuildConfig.VERSION_NAME} -> $normalizedVersionName ($currentVersion), code=$currentVersionCode, dev=$isDevUpdateChannel, token=${GithubUpdateAuth.isConfigured(settings)}",
			)

			val available = getAvailableVersions().asArrayList()
			android.util.Log.d("UPDATE_DEBUG", "Found ${available.size} available versions:")
			available.forEach { version ->
				android.util.Log.d("UPDATE_DEBUG", "  - ${version.name} code=${version.versionId} vc=${version.versionCode} (stable: ${version.versionId.isStable})")
			}

			if (currentVersion.isStable && !settings.isUnstableUpdatesAllowed && !isDevUpdateChannel) {
				val beforeFiltering = available.size
				available.retainAll { it.versionId.isStable }
				android.util.Log.d("UPDATE_DEBUG", "Filtered unstable versions: $beforeFiltering -> ${available.size}")
			}

			val result = selectLatestUpdate(normalizedVersionName, currentVersionCode, available, isDevUpdateChannel)
			android.util.Log.d("UPDATE_DEBUG", "Update result: ${result?.name} (vc=${result?.versionCode})")

			result
		}.onFailure {
			android.util.Log.e("UPDATE_DEBUG", "Error during update check", it)
			it.printStackTraceDebug()
		}.onSuccess {
			android.util.Log.d("UPDATE_DEBUG", "Setting availableUpdate to: ${it?.name}")
			availableUpdate.value = it
		}.getOrNull()
	}

	suspend fun isUpdateSupported(): Boolean = true

	private suspend fun fetchAllReleasePages(requestBuilder: Request.Builder): JSONArray {
		val merged = JSONArray()
		for (page in 1..MAX_RELEASE_PAGES) {
			val url = "$releasesUrlBase?per_page=$RELEASES_PER_PAGE&page=$page"
			val response = okHttp.newCall(
				requestBuilder.url(url).build(),
			).await()
			if (!response.isSuccessful) {
				android.util.Log.e(
					"UPDATE_DEBUG",
					"GitHub releases API failed: HTTP ${response.code} (token=${GithubUpdateAuth.isConfigured(settings)})",
				)
				if (response.code == 401 || response.code == 403 || response.code == 404) {
					updateCheckMessage.value = if (isDevUpdateChannel) {
						context.getString(R.string.ota_dev_token_expired)
					} else {
						context.getString(R.string.ota_release_token_expired)
					}
					android.util.Log.e(
						"UPDATE_DEBUG",
						if (isDevUpdateChannel) {
							"Dev token cannot read private repo — update kosen.dev_github_token in local.properties and rebuild"
						} else {
							"Release token cannot read private repo — update kosen.release_github_token in local.properties and rebuild"
						},
					)
				}
				response.close()
				break
			}
			val pageArray = response.parseJsonArray()
			response.close()
			android.util.Log.d("UPDATE_DEBUG", "GitHub API page $page returned ${pageArray.length()} releases")
			if (pageArray.length() == 0) {
				break
			}
			for (i in 0 until pageArray.length()) {
				merged.put(pageArray.getJSONObject(i))
			}
			if (pageArray.length() < RELEASES_PER_PAGE) {
				break
			}
		}
		android.util.Log.d("UPDATE_DEBUG", "GitHub API total releases loaded: ${merged.length()}")
		return merged
	}

	private inline fun JSONArray.find(predicate: (JSONObject) -> Boolean): JSONObject? {
		val size = length()
		for (i in 0 until size) {
			val jo = getJSONObject(i)
			if (predicate(jo)) {
				return jo
			}
		}
		return null
	}
}
