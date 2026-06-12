package com.kosen.reader.core.exceptions

class SyncApiException(
	message: String,
	val code: Int,
) : RuntimeException(message)
