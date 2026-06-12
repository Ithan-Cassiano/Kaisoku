package com.kosen.reader.core.model

import java.util.Locale
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.parsers.model.ContentRating
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource

private val STRICT_ADULT_TAG_KEYWORDS = setOf(
	"hentai",
	"+18",
	"18+",
	"adult",
	"adulto",
	"mature",
	"nsfw",
	"r18",
	"r-18",
	"smut",
	"erotica",
	"porn",
	"porno",
	"xxx",
)

private val MODERATE_ADULT_TAG_KEYWORDS = setOf(
	"echi",
	"ecchi",
	"harem",
	"harém",
)

fun Manga.isAdultContentFiltered(settings: AppSettings): Boolean {
	if ((source as? MangaParserSource)?.contentType == com.kosen.reader.parsers.model.ContentType.HENTAI) {
		return true
	}
	if (contentRating == ContentRating.ADULT) {
		return true
	}
	val tagTitles = tags.map { it.title.lowercase(Locale.ROOT) }
	if (tagTitles.any { it in STRICT_ADULT_TAG_KEYWORDS }) {
		return true
	}
	if (!settings.isNsfwFilterStrict && tagTitles.any { it in MODERATE_ADULT_TAG_KEYWORDS }) {
		return true
	}
	return false
}
