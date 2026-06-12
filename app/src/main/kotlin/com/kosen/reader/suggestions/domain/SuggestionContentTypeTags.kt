package com.kosen.reader.suggestions.domain

import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaTag

private val CONTENT_TYPE_TAG_LABELS = mapOf(
	ContentType.MANGA to "Manga",
	ContentType.MANHWA to "Manhwa",
	ContentType.MANHUA to "Manhua",
	ContentType.COMICS to "Comics",
	ContentType.NOVEL to "Novel",
	ContentType.ONE_SHOT to "One shot",
	ContentType.DOUJINSHI to "Doujinshi",
)

fun Manga.withSuggestionContentTypeTag(): Manga {
	val parserSource = source as? MangaParserSource ?: return this
	val label = CONTENT_TYPE_TAG_LABELS[parserSource.contentType] ?: return this
	if (tags.any { it.title.equals(label, ignoreCase = true) }) {
		return this
	}
	return copy(tags = tags + MangaTag(title = label, key = label.lowercase(), source = source))
}
