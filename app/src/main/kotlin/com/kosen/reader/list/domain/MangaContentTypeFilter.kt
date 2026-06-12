package com.kosen.reader.list.domain

import android.database.DatabaseUtils.sqlEscapeString
import java.util.Locale
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource

private val CONTENT_TYPE_TAG_NAMES: Map<ContentType, Set<String>> = mapOf(
	ContentType.MANGA to setOf("manga", "mangá", "mangas"),
	ContentType.MANHWA to setOf("manhwa", "manhwas"),
	ContentType.MANHUA to setOf("manhua", "manhuas"),
	ContentType.COMICS to setOf("comics", "comic", "hq", "hqs", "quadrinhos"),
	ContentType.NOVEL to setOf("novel", "novels", "light novel", "light novels"),
	ContentType.ONE_SHOT to setOf("one shot", "one-shot", "oneshot"),
	ContentType.DOUJINSHI to setOf("doujinshi", "doujin", "doujins"),
)

fun Manga.matchesContentTypeFilter(contentType: ContentType): Boolean = when (contentType) {
	ContentType.HENTAI -> (source as? MangaParserSource)?.contentType == ContentType.HENTAI
	else -> {
		val tagNames = CONTENT_TYPE_TAG_NAMES[contentType]
		if (tagNames != null && tags.any { tag -> tag.title.lowercase(Locale.ROOT) in tagNames }) {
			true
		} else {
			(source as? MangaParserSource)?.contentType == contentType &&
				contentType !in TAG_ONLY_CONTENT_TYPES
		}
	}
}

fun mangaContentTypeSql(mangaIdExpr: String, contentType: ContentType): String? = when (contentType) {
	ContentType.HENTAI -> {
		val sources = parserSourcesOfType(contentType)
		if (sources.isEmpty()) {
			null
		} else {
			"(SELECT source FROM manga WHERE manga.manga_id = $mangaIdExpr) IN ($sources)"
		}
	}
	else -> {
		val tagNames = CONTENT_TYPE_TAG_NAMES[contentType]
		val tagCondition = tagNames?.let { names ->
			val tagsSql = names.joinToString(",") { sqlEscapeString(it) }
			"EXISTS(SELECT 1 FROM manga_tags mt INNER JOIN tags t ON t.tag_id = mt.tag_id " +
				"WHERE mt.manga_id = $mangaIdExpr AND LOWER(t.title) IN ($tagsSql))"
		}
		val sourceCondition = if (contentType in TAG_ONLY_CONTENT_TYPES) {
			null
		} else {
			val sources = parserSourcesOfType(contentType)
			if (sources.isEmpty()) null
			else "(SELECT source FROM manga WHERE manga.manga_id = $mangaIdExpr) IN ($sources)"
		}
		when {
			tagCondition != null && sourceCondition != null -> "($tagCondition OR $sourceCondition)"
			tagCondition != null -> tagCondition
			sourceCondition != null -> sourceCondition
			else -> null
		}
	}
}

private fun parserSourcesOfType(contentType: ContentType): String =
	MangaParserSource.entries
		.asSequence()
		.filter { it.contentType == contentType }
		.joinToString(
			transform = { sqlEscapeString(it.name) },
		)

private val TAG_ONLY_CONTENT_TYPES = setOf(
	ContentType.MANGA,
	ContentType.MANHWA,
	ContentType.MANHUA,
)
