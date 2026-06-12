package com.kosen.reader.explore.domain

import org.json.JSONObject
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.model.MangaSource as resolveMangaSource
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.MangaSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourcePreferenceByTypeRepository @Inject constructor(
	private val settings: AppSettings,
) {

	fun getPreferredSource(type: ContentType): MangaSource? {
		val raw = settings.sourcePreferencesByTypeJson ?: return null
		return runCatching {
			val name = JSONObject(raw).optString(type.name, "")
			if (name.isEmpty()) null else resolveMangaSource(name)
		}.getOrNull()
	}

	fun setPreferredSource(type: ContentType, source: MangaSource) {
		val json = runCatching {
			JSONObject(settings.sourcePreferencesByTypeJson.orEmpty())
		}.getOrElse { JSONObject() }
		json.put(type.name, source.name)
		settings.sourcePreferencesByTypeJson = json.toString()
	}

	fun rememberLastSourceForType(type: ContentType, source: MangaSource) {
		setPreferredSource(type, source)
	}
}
