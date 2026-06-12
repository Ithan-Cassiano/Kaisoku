package com.kosen.reader.core.exceptions

import com.kosen.reader.parsers.model.Manga

class UnsupportedSourceException(
	message: String?,
	val manga: Manga?,
) : IllegalArgumentException(message)
