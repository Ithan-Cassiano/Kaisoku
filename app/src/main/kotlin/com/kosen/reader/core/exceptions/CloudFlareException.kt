package com.kosen.reader.core.exceptions

import okio.IOException
import com.kosen.reader.parsers.model.MangaSource

abstract class CloudFlareException(
	message: String,
	val state: Int,
) : IOException(message) {

	abstract val url: String

	abstract val source: MangaSource
}
