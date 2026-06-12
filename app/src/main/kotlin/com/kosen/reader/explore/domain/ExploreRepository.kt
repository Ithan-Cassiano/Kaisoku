package com.kosen.reader.explore.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.kosen.reader.core.model.isAdultContent
import com.kosen.reader.list.domain.matchesContentTypeFilter
import com.kosen.reader.core.model.isNsfw
import com.kosen.reader.list.domain.isNsfwExcluded
import com.kosen.reader.list.domain.isNsfwOnly
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.util.ext.asArrayList
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.almostEquals
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.suggestions.domain.TagsBlacklist
import javax.inject.Inject

class ExploreRepository @Inject constructor(
	private val settings: AppSettings,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend fun findRandomManga(tagsLimit: Int): Manga {
		val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val tags = historyRepository.getPopularTags(tagsLimit).mapNotNull {
			if (it in tagsBlacklist) null else it.title
		}
		val sources = sourcesRepository.getEnabledSources()
		check(sources.isNotEmpty()) { "No sources available" }
		for (i in 0..4) {
			val list = getList(sources.random(), tags, tagsBlacklist)
			val manga = list.randomOrNull() ?: continue
			val details = runCatchingCancellable {
				mangaRepositoryFactory.create(manga.source).getDetails(manga)
			}.getOrNull() ?: continue
			if ((settings.isSuggestionsExcludeNsfw && details.isAdultContent()) || details in tagsBlacklist) {
				continue
			}
			return details
		}
		throw NoSuchElementException()
	}

	suspend fun findRandomManga(source: MangaSource, tagsLimit: Int): Manga {
		val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val skipNsfw = settings.isSuggestionsExcludeNsfw && !source.isNsfw()
		val tags = historyRepository.getPopularTags(tagsLimit).mapNotNull {
			if (it in tagsBlacklist) null else it.title
		}
		for (i in 0..4) {
			val list = getList(source, tags, tagsBlacklist)
			val manga = list.randomOrNull() ?: continue
			val details = runCatchingCancellable {
				mangaRepositoryFactory.create(manga.source).getDetails(manga)
			}.getOrNull() ?: continue
			if ((skipNsfw && details.isAdultContent()) || details in tagsBlacklist) {
				continue
			}
			return details
		}
		throw NoSuchElementException()
	}

	suspend fun getQuickSuggestions(count: Int, filterOptions: Set<ListFilterOption>): List<Manga> {
		if (count <= 0) {
			return emptyList()
		}
		val tagsBlacklist = TagsBlacklist(settings.suggestionsTagsBlacklist, 0.4f)
		val tags = historyRepository.getPopularTags(8).mapNotNull {
			if (it in tagsBlacklist) null else it.title
		}
		val sources = sourcesRepository.getEnabledSources().shuffled().take(QUICK_SOURCES)
		if (sources.isEmpty()) {
			return emptyList()
		}
		val semaphore = Semaphore(QUICK_SOURCES)
		val candidates = coroutineScope {
			sources.map { source ->
				async {
					semaphore.withPermit {
						getQuickList(source, tags, tagsBlacklist, filterOptions)
							.filter { manga -> manga.matchesExploreFilters(filterOptions) }
							.shuffled()
							.take(QUICK_PER_SOURCE)
					}
				}
			}.awaitAll().flatten()
		}
		return candidates.shuffled().take(count)
	}

	private fun Manga.matchesExploreFilters(filterOptions: Set<ListFilterOption>): Boolean {
		for (option in filterOptions) {
			when (option) {
				ListFilterOption.Macro.NSFW -> if (!isAdultContent()) return false
				is ListFilterOption.Inverted -> when (val base = option.option) {
					ListFilterOption.Macro.NSFW -> if (isAdultContent()) return false
					is ListFilterOption.ContentType -> {
						if (matchesContentTypeFilter(base.contentType)) return false
					}
					is ListFilterOption.Tag -> if (tags.any { it.title == base.tag.title }) return false
					is ListFilterOption.Source -> if (source == base.mangaSource) return false
					else -> Unit
				}
				is ListFilterOption.Source -> if (source != option.mangaSource) return false
				is ListFilterOption.Tag -> if (tags.none { it.title == option.tag.title }) return false
				is ListFilterOption.ContentType -> {
					if (!matchesContentTypeFilter(option.contentType)) return false
				}
				else -> Unit
			}
		}
		return true
	}

	private suspend fun getQuickList(
		source: MangaSource,
		tags: List<String>,
		blacklist: TagsBlacklist,
		filterOptions: Set<ListFilterOption>,
	): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(source)
		val order = quickSortOrders.firstOrNull { it in repository.sortOrders }
			?: repository.sortOrders.first()
		val availableTags = repository.getFilterOptions().availableTags
		val tag = tags.firstNotNullOfOrNull { title ->
			availableTags.find { x -> x.title.almostEquals(title, 0.4f) }
		}
		val list = repository.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(tags = setOfNotNull(tag)),
		).asArrayList()
		if (isNsfwExcluded(filterOptions) || settings.isSuggestionsExcludeNsfw || settings.isNsfwContentDisabled) {
			if (!isNsfwOnly(filterOptions)) {
				list.removeAll { it.isAdultContent() }
			}
		}
		if (blacklist.isNotEmpty()) {
			list.removeAll { manga -> manga in blacklist }
		}
		list
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrDefault(emptyList())

	private suspend fun getList(
		source: MangaSource,
		tags: List<String>,
		blacklist: TagsBlacklist,
	): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(source)
		val order = repository.sortOrders.random()
		val availableTags = repository.getFilterOptions().availableTags
		val tag = tags.firstNotNullOfOrNull { title ->
			availableTags.find { x -> x.title.almostEquals(title, 0.4f) }
		}
		val list = repository.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(tags = setOfNotNull(tag)),
		).asArrayList()
		if (settings.isSuggestionsExcludeNsfw) {
			list.removeAll { it.isAdultContent() }
		}
		if (blacklist.isNotEmpty()) {
			list.removeAll { manga -> manga in blacklist }
		}
		list.shuffle()
		list
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrDefault(emptyList())

	private companion object {
		const val QUICK_SOURCES = 4
		const val QUICK_PER_SOURCE = 4

		val quickSortOrders = listOf(
			SortOrder.POPULARITY,
			SortOrder.UPDATED,
			SortOrder.RATING,
			SortOrder.NEWEST,
		)
	}
}
