package com.kosen.reader.core.exceptions

import okhttp3.Headers
import com.kosen.reader.core.model.UnknownMangaSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.network.CloudFlareHelper

class CloudFlareProtectedException(
	override val url: String,
	source: MangaSource?,
	@Transient val headers: Headers,
) : CloudFlareException("Protected by CloudFlare", CloudFlareHelper.PROTECTION_CAPTCHA) {

	override val source: MangaSource = source ?: UnknownMangaSource
}
