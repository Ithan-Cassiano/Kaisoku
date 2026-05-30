package org.koitharu.kotatsu.core.parser

import org.koitharu.kotatsu.parsers.config.ConfigKey

fun ParserMangaRepository.getAuthSessionKey(): ConfigKey.AuthSession? =
	getConfigKeys().filterIsInstance<ConfigKey.AuthSession>().firstOrNull()

fun ParserMangaRepository.isAuthSessionConfirmed(): Boolean {
	val key = getAuthSessionKey() ?: return false
	return getConfig()[key]
}

fun ParserMangaRepository.setAuthSessionConfirmed(confirmed: Boolean) {
	getAuthSessionKey()?.let { key ->
		getConfig()[key] = confirmed
	}
}
