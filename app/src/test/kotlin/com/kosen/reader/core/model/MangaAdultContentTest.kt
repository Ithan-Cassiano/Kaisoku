package com.kosen.reader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.kosen.reader.parsers.model.ContentRating
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag

class MangaAdultContentTest {

	@Test
	fun isAdultContent_detectsContentRating() {
		val manga = sampleManga(contentRating = ContentRating.ADULT)
		assertTrue(manga.isAdultContent())
	}

	@Test
	fun isAdultContent_detectsHentaiSource() {
		val manga = sampleManga(source = MangaParserSource.MEGAHENTAI)
		assertTrue(manga.isAdultContent())
	}

	@Test
	fun isAdultContent_detectsAdultTags() {
		val manga = sampleManga(tags = setOf(tag("Hentai")))
		assertTrue(manga.isAdultContent())
	}

	@Test
	fun isAdultContent_safeMangaIsNotAdult() {
		val manga = sampleManga(tags = setOf(tag("Action")))
		assertFalse(manga.isAdultContent())
	}

	private fun sampleManga(
		contentRating: ContentRating? = null,
		source: MangaParserSource = MangaParserSource.MANGADEX,
		tags: Set<MangaTag> = emptySet(),
	) = Manga(
		id = 1L,
		title = "Test",
		altTitles = emptySet(),
		url = "https://example.com/manga",
		publicUrl = "https://example.com/manga",
		rating = -1f,
		contentRating = contentRating,
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
