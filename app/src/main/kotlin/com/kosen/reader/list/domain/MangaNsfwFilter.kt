package com.kosen.reader.list.domain

import android.database.DatabaseUtils.sqlEscapeString
import com.kosen.reader.core.model.isAdultContent
import com.kosen.reader.core.model.isAdultContentFiltered
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource

fun isNsfwExcluded(filters: Set<ListFilterOption>): Boolean =
	filters.any { option ->
		option is ListFilterOption.Inverted && option.option == ListFilterOption.Macro.NSFW
	}

fun isNsfwOnly(filters: Set<ListFilterOption>): Boolean =
	ListFilterOption.Macro.NSFW in filters

fun List<Manga>.filterByNsfwOptions(
	filters: Set<ListFilterOption>,
	settings: AppSettings? = null,
): List<Manga> {
	val isAdult: (Manga) -> Boolean = if (settings != null) {
		{ it.isAdultContentFiltered(settings) }
	} else {
		{ it.isAdultContent() }
	}
	return when {
		isNsfwOnly(filters) -> filter(isAdult)
		isNsfwExcluded(filters) -> filterNot(isAdult)
		else -> this
	}
}

fun mangaIdAdultContentSql(mangaIdExpr: String): String {
	val hentaiSources = MangaParserSource.entries
		.filter { it.contentType == ContentType.HENTAI }
		.joinToString(",") { sqlEscapeString(it.name) }
	val adultTags = ADULT_TAG_SQL_TITLES.joinToString(",") { sqlEscapeString(it) }
	return buildString {
		append('(')
		append("(SELECT nsfw FROM manga WHERE manga.manga_id = $mangaIdExpr) = 1")
		append(" OR (SELECT content_rating FROM manga WHERE manga.manga_id = $mangaIdExpr) = 'ADULT'")
		if (hentaiSources.isNotEmpty()) {
			append(" OR (SELECT source FROM manga WHERE manga.manga_id = $mangaIdExpr) IN ($hentaiSources)")
		}
		append(
			" OR EXISTS(SELECT 1 FROM manga_tags mt INNER JOIN tags t ON t.tag_id = mt.tag_id " +
				"WHERE mt.manga_id = $mangaIdExpr AND LOWER(t.title) IN ($adultTags))",
		)
		append(')')
	}
}

fun ListFilterOption.Macro.nsfwSqlCondition(mangaIdExpr: String): String =
	mangaIdAdultContentSql(mangaIdExpr)

private val ADULT_TAG_SQL_TITLES = setOf(
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
