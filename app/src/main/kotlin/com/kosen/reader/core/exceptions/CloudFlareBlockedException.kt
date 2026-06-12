package com.kosen.reader.core.exceptions

import com.kosen.reader.core.model.UnknownMangaSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.network.CloudFlareHelper

class CloudFlareBlockedException(
	override val url: String,
	source: MangaSource?,
) : CloudFlareException("Blocked by CloudFlare", CloudFlareHelper.PROTECTION_BLOCKED) {

	override val source: MangaSource = source ?: UnknownMangaSource
}
