package org.koitharu.kotatsu.reader.translation

import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject

enum class PageTranslationProvider {
	OPENAI_COMPATIBLE,
	LIBRE_TRANSLATE,
}

enum class PageTranslationOcrMode {
	AUTO,
	JAPANESE,
	LATIN,
}

data class PageTranslationConfig(
	val provider: PageTranslationProvider,
	val endpoint: String,
	val apiKey: String?,
	val model: String?,
	val targetLanguage: String,
	val ocrMode: PageTranslationOcrMode,
) {

	fun isConfigured(): Boolean {
		return endpoint.isNotBlank() && targetLanguage.isNotBlank() && when (provider) {
			PageTranslationProvider.OPENAI_COMPATIBLE -> !model.isNullOrBlank()
			PageTranslationProvider.LIBRE_TRANSLATE -> true
		}
	}
}

class PageTranslationSettings @Inject constructor(
	private val settings: AppSettings,
) {

	fun getConfig() = PageTranslationConfig(
		provider = settings.pageTranslationProvider,
		endpoint = settings.pageTranslationEndpoint.orEmpty(),
		apiKey = settings.pageTranslationApiKey,
		model = settings.pageTranslationModel,
		targetLanguage = settings.pageTranslationTargetLanguage,
		ocrMode = settings.pageTranslationOcrMode,
	)

	fun isConfigured(): Boolean = getConfig().isConfigured()
}
