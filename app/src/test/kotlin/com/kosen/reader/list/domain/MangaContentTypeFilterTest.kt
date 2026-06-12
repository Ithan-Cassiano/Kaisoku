package com.kosen.reader.list.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kosen.reader.core.model.LocalMangaSource
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaTag

class MangaContentTypeFilterTest {

	@Test
	fun excludeManga_doesNotMatchUntaggedPlumaSource() {
		val manga = sampleManga(
			source = MangaParserSource.PLUMACOMICS,
			tags = setOf(tag("Ação")),
		)
		assertFalse(manga.matchesContentTypeFilter(ContentType.MANGA))
	}

	@Test
	fun excludeManga_matchesExplicitTag() {
		val manga = sampleManga(tags = setOf(tag("Mangá")))
		assertTrue(manga.matchesContentTypeFilter(ContentType.MANGA))
	}

	@Test
	fun excludeManhua_matchesExplicitTag() {
		val manga = sampleManga(tags = setOf(tag("Manhua")))
		assertTrue(manga.matchesContentTypeFilter(ContentType.MANHUA))
	}

	@Test
	fun hentai_matchesHentaiSource() {
		val manga = sampleManga(source = MangaParserSource.MEGAHENTAI)
		assertTrue(manga.matchesContentTypeFilter(ContentType.HENTAI))
	}

	private fun sampleManga(
		source: MangaParserSource = MangaParserSource.MANGADEX,
		tags: Set<MangaTag> = emptySet(),
	) = Manga(
		id = 1L,
		title = "Test",
		altTitles = emptySet(),
		url = "/test",
		publicUrl = "https://example.com/test",
		rating = -1f,
		contentRating = null,
		coverUrl = "",
		tags = tags,
		state = null,
		authors = emptySet(),
		source = source,
	)

	private fun tag(title: String) = MangaTag(
		key = title.lowercase(),
		title = title,
		source = LocalMangaSource,
	)
}
