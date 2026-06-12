package com.kosen.reader.core.exceptions

import okio.IOException
import com.kosen.reader.parsers.model.MangaSource

class InteractiveActionRequiredException(
	val source: MangaSource,
	val url: String,
) : IOException("Interactive action is required for ${source.name}")
