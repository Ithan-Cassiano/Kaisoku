package com.kosen.reader.core.parser

import android.net.Uri

internal object LegacyAppLinkSchemes {

	private val APP_SCHEMES = setOf("kosen", "kaisoku", "kotatsu")

	fun isAppScheme(scheme: String?): Boolean {
		return scheme != null && scheme.lowercase() in APP_SCHEMES
	}

	fun isLegacyAppLink(uri: Uri): Boolean {
		return isAppScheme(uri.scheme)
			|| uri.host == "kotatsu.app"
			|| uri.host == "kaisoku.app"
	}

	fun isValidAppLink(str: String): Boolean {
		return str.startsWith("kosen://", ignoreCase = true)
			|| str.startsWith("kaisoku://", ignoreCase = true)
			|| str.startsWith("kotatsu://", ignoreCase = true)
	}
}
