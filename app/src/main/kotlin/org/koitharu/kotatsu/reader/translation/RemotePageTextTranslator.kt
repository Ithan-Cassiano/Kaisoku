package org.koitharu.kotatsu.reader.translation

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import eu.kanade.tachiyomi.network.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.util.ext.ensureSuccess
import org.koitharu.kotatsu.core.util.ext.sanitizeHeaderValue
import org.koitharu.kotatsu.core.util.ext.toRequestBody
import javax.inject.Inject
import java.util.concurrent.TimeUnit

class RemotePageTextTranslator @Inject constructor(
	@BaseHttpClient private val okHttpClient: OkHttpClient,
) {

	private val translationClient = okHttpClient.newBuilder()
		.readTimeout(3, TimeUnit.MINUTES)
		.callTimeout(4, TimeUnit.MINUTES)
		.build()

	suspend fun translate(
		blocks: List<PageTranslationBlock>,
		config: PageTranslationConfig,
		traceId: String,
	): List<String> = when (config.provider) {
		PageTranslationProvider.OPENAI_COMPATIBLE -> translateOpenAiCompatible(blocks, config, traceId)
		PageTranslationProvider.LIBRE_TRANSLATE -> translateLibreTranslate(blocks, config, traceId)
	}

	private suspend fun translateOpenAiCompatible(
		blocks: List<PageTranslationBlock>,
		config: PageTranslationConfig,
		traceId: String,
	): List<String> {
		val startedAt = SystemClock.elapsedRealtime()
		Log.d(
			TAG,
			"[$traceId] Remote request provider=${config.provider.name} host=${Uri.parse(config.endpoint.toOpenAiEndpoint()).host ?: "unknown"} model=${config.model ?: "unset"} blocks=${blocks.size} chars=${blocks.sumOf { it.text.length }}",
		)
		val messages = JSONArray()
			.put(
				JSONObject()
					.put("role", "system")
					.put(
						"content",
						"""
						You translate manga OCR text into ${config.targetLanguage}.
						Return only valid JSON.
						Format: {"translations":[{"id":0,"text":"..." }]}
						Rules:
						- Keep the same ids and number of items.
						- Keep each translation concise enough to fit speech bubbles.
						- Preserve tone.
						- If the OCR text is unclear, return the original text for that item.
						""".trimIndent(),
					),
			)
			.put(
				JSONObject()
					.put("role", "user")
					.put(
						"content",
						JSONObject()
							.put("blocks", JSONArray().apply {
								blocks.forEachIndexed { index, block ->
									put(
										JSONObject()
											.put("id", index)
											.put("text", block.text),
									)
								}
							})
							.toString(),
					),
			)
		val body = JSONObject()
			.put("model", config.model)
			.put("temperature", 0.2)
			.put("stream", false)
			.put("messages", messages)
		val request = Request.Builder()
			.url(config.endpoint.toOpenAiEndpoint())
			.post(body.toRequestBody())
			.header("Accept", "application/json")
			.apply {
				config.apiKey?.takeIf { it.isNotBlank() }?.let {
					header("Authorization", "Bearer ${it.sanitizeHeaderValue()}")
				}
			}
			.build()
		val responseJson = translationClient.newCall(request).await().use { response ->
			Log.d(TAG, "[$traceId] Remote response code=${response.code} message=${response.message}")
			response.ensureSuccess()
			JSONObject(response.body?.string().orEmpty())
		}
		val content = responseJson
			.getJSONArray("choices")
			.getJSONObject(0)
			.getJSONObject("message")
			.optString("content")
			.ifBlank {
				responseJson.getJSONArray("choices")
					.getJSONObject(0)
					.optString("text")
			}
		check(content.isNotBlank()) { "Translation response is empty" }
		return parseOpenAiTranslations(content, blocks.map { it.text }).also {
			Log.d(
				TAG,
				"[$traceId] Parsed ${it.size} translations in ${SystemClock.elapsedRealtime() - startedAt}ms",
			)
		}
	}

	private suspend fun translateLibreTranslate(
		blocks: List<PageTranslationBlock>,
		config: PageTranslationConfig,
		traceId: String,
	): List<String> {
		val startedAt = SystemClock.elapsedRealtime()
		Log.d(
			TAG,
			"[$traceId] Remote request provider=${config.provider.name} host=${Uri.parse(config.endpoint.toLibreTranslateEndpoint()).host ?: "unknown"} blocks=${blocks.size} chars=${blocks.sumOf { it.text.length }}",
		)
		val body = JSONObject()
			.put("q", JSONArray(blocks.map { it.text }))
			.put("source", "auto")
			.put("target", config.targetLanguage)
			.put("format", "text")
			.apply {
				config.apiKey?.takeIf { it.isNotBlank() }?.let {
					put("api_key", it)
				}
			}
		val request = Request.Builder()
			.url(config.endpoint.toLibreTranslateEndpoint())
			.post(body.toRequestBody())
			.header("Accept", "application/json")
			.build()
		val responseJson = translationClient.newCall(request).await().use { response ->
			Log.d(TAG, "[$traceId] Remote response code=${response.code} message=${response.message}")
			response.ensureSuccess()
			JSONObject(response.body?.string().orEmpty())
		}
		val translated = responseJson.opt("translatedText")
		return when (translated) {
			is JSONArray -> List(blocks.size) { index ->
				translated.optString(index).ifBlank { blocks[index].text }
			}

			is String -> List(blocks.size) { index ->
				if (index == 0) {
					translated.ifBlank { blocks[index].text }
				} else {
					blocks[index].text
				}
			}
			else -> error("Unexpected translation response")
		}.also {
			Log.d(
				TAG,
				"[$traceId] Parsed ${it.size} translations in ${SystemClock.elapsedRealtime() - startedAt}ms",
			)
		}
	}

	private fun parseOpenAiTranslations(
		rawContent: String,
		originalTexts: List<String>,
	): List<String> {
		val payload = extractJsonPayload(rawContent)
		val array = when {
			payload.startsWith("{") -> JSONObject(payload).optJSONArray("translations")
			payload.startsWith("[") -> JSONArray(payload)
			else -> null
		} ?: error("Unexpected translation response")
		val resolved = MutableList<String?>(originalTexts.size) { null }
		for (index in 0 until array.length()) {
			when (val item = array.get(index)) {
				is String -> if (index in resolved.indices) {
					resolved[index] = item
				}

				is JSONObject -> {
					val id = item.optInt("id", index)
					if (id in resolved.indices) {
						resolved[id] = item.optString("text", originalTexts[id])
					}
				}
			}
		}
		check(resolved.any { !it.isNullOrBlank() }) { "Translation response is empty" }
		return resolved.mapIndexed { index, value ->
			value?.trim().takeUnless { it.isNullOrEmpty() } ?: originalTexts[index]
		}
	}

	private fun extractJsonPayload(rawContent: String): String {
		val trimmed = rawContent.trim()
			.removeSurrounding("```json", "```")
			.removeSurrounding("```", "```")
			.trim()
		val objectStart = trimmed.indexOf('{')
		val objectEnd = trimmed.lastIndexOf('}')
		if (objectStart >= 0 && objectEnd > objectStart) {
			return trimmed.substring(objectStart, objectEnd + 1)
		}
		val arrayStart = trimmed.indexOf('[')
		val arrayEnd = trimmed.lastIndexOf(']')
		if (arrayStart >= 0 && arrayEnd > arrayStart) {
			return trimmed.substring(arrayStart, arrayEnd + 1)
		}
		return trimmed
	}

	private fun String.toOpenAiEndpoint(): String {
		val trimmed = trim().trimEnd('/')
		return when {
			trimmed.endsWith("/chat/completions") -> trimmed
			trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
			else -> "$trimmed/v1/chat/completions"
		}
	}

	private fun String.toLibreTranslateEndpoint(): String {
		val trimmed = trim().trimEnd('/')
		return if (trimmed.endsWith("/translate")) {
			trimmed
		} else {
			"$trimmed/translate"
		}
	}

	private companion object {
		private const val TAG = "PageTranslation"
	}
}
