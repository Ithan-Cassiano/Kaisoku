package com.kosen.reader.core.parser

import com.kosen.reader.core.cache.MemoryContentCache
import com.kosen.reader.core.model.TestMangaSource
import com.kosen.reader.parsers.MangaLoaderContext
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaChapter
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaListFilterCapabilities
import com.kosen.reader.parsers.model.MangaListFilterOptions
import com.kosen.reader.parsers.model.MangaPage
import com.kosen.reader.parsers.model.MangaState
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.longHashCode
import java.util.EnumSet

/*
 This class is for parser development and testing purposes
 You can open it in the app via Settings -> Debug
 */
class TestMangaRepository(
	@Suppress("unused") private val loaderContext: MangaLoaderContext,
	cache: MemoryContentCache,
) : CachingMangaRepository(cache) {

	override val source = TestMangaSource

	override val sortOrders: Set<SortOrder> = EnumSet.allOf(SortOrder::class.java)

	override var defaultSortOrder: SortOrder
		get() = sortOrders.first()
		set(value) = Unit

	override val filterCapabilities = MangaListFilterCapabilities(
		isSearchSupported = true,
	)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(
		offset: Int,
		order: SortOrder?,
		filter: MangaListFilter?,
	): List<Manga> {
		val query = filter?.query?.trim()?.lowercase()
		val list = if (query.isNullOrEmpty()) {
			catalog
		} else {
			catalog.filter { manga ->
				manga.title.lowercase().contains(query)
			}
		}
		return if (offset >= list.size) {
			emptyList()
		} else {
			list.drop(offset)
		}
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val base = catalog.find { it.url == manga.url } ?: manga
		return base.copy(
			description = "Sample manga for parser development. Replace TestMangaRepository with your implementation.",
			state = MangaState.ONGOING,
			chapters = List(CHAPTER_COUNT) { index ->
				val number = index + 1
				val chapterUrl = "${base.url}/chapter-$number"
				MangaChapter(
					id = chapterUrl.longHashCode(),
					title = "Chapter $number",
					number = number.toFloat(),
					volume = 0,
					url = chapterUrl,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
		return List(PAGE_COUNT) { index ->
			val pageNumber = index + 1
			val pageUrl = "${chapter.url}/page-$pageNumber"
			MangaPage(
				id = pageUrl.longHashCode(),
				url = "https://picsum.photos/seed/${chapter.id}-$pageNumber/800/1200",
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String = page.url

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> {
		return catalog.filterNot { it.url == seed.url }.take(2)
	}

	private companion object {

		const val CHAPTER_COUNT = 5
		const val PAGE_COUNT = 3

		val catalog = listOf(
			createSampleManga("Sample Manga Alpha", "/alpha", "kosen-alpha"),
			createSampleManga("Sample Manga Beta", "/beta", "kosen-beta"),
			createSampleManga("Sample Manga Gamma", "/gamma", "kosen-gamma"),
		)

		private fun createSampleManga(
			title: String,
			path: String,
			coverSeed: String,
		): Manga {
			val url = "https://test.kosen.local$path"
			return Manga(
				id = url.longHashCode(),
				title = title,
				altTitles = emptySet(),
				url = url,
				publicUrl = url,
				rating = 0f,
				contentRating = null,
				coverUrl = "https://picsum.photos/seed/$coverSeed/300/450",
				largeCoverUrl = "https://picsum.photos/seed/$coverSeed/600/900",
				tags = emptySet(),
				state = null,
				authors = emptySet(),
				source = TestMangaSource,
			)
		}
	}
}
