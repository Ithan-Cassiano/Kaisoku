package com.kosen.reader.settings.sources.auth

import okhttp3.Headers
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.parser.ParserMangaRepository
import com.kosen.reader.core.parser.setAuthSessionConfirmed
import com.kosen.reader.core.parser.PluginMangaRepository
import com.kosen.reader.core.parser.getAuthSessionKey
import com.kosen.reader.parsers.MangaParserAuthProvider

internal interface SourceAuthRepository {

	fun getAuthProvider(): MangaParserAuthProvider?

	fun setAuthSessionConfirmed(confirmed: Boolean)

	fun getRequestHeaders(): Headers
}

internal fun MangaRepository.asSourceAuthRepository(): SourceAuthRepository? = when (this) {
	is ParserMangaRepository -> ParserSourceAuthRepository(this)
	is PluginMangaRepository -> PluginSourceAuthRepository(this)
	else -> null
}

private class ParserSourceAuthRepository(
	private val repository: ParserMangaRepository,
) : SourceAuthRepository {

	override fun getAuthProvider(): MangaParserAuthProvider? = repository.getAuthProvider()

	override fun setAuthSessionConfirmed(confirmed: Boolean) = repository.setAuthSessionConfirmed(confirmed)

	override fun getRequestHeaders(): Headers = repository.getRequestHeaders()
}

private class PluginSourceAuthRepository(
	private val repository: PluginMangaRepository,
) : SourceAuthRepository {

	override fun getAuthProvider(): MangaParserAuthProvider? = repository.getAuthProvider()

	override fun setAuthSessionConfirmed(confirmed: Boolean) {
		repository.getAuthSessionKey()?.let { key ->
			repository.getConfig()[key] = confirmed
		}
	}

	override fun getRequestHeaders(): Headers = repository.getRequestHeaders()
}

private fun PluginMangaRepository.getAuthSessionKey() =
	getConfigKeys().filterIsInstance<com.kosen.reader.parsers.config.ConfigKey.AuthSession>().firstOrNull()
