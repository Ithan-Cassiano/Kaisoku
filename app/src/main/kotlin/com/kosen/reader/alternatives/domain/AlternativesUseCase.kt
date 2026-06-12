package com.kosen.reader.alternatives.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import com.kosen.reader.core.model.identityName
import com.kosen.reader.core.model.unwrap
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.util.ext.toLocale
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.search.domain.SearchKind
import com.kosen.reader.search.domain.SearchV2Helper
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

private const val MAX_SOURCES = 12
private const val MAX_TOTAL_RESULTS = 20
private const val MAX_CANDIDATES_PER_SOURCE = 2
private const val MAX_SEARCH_PARALLELISM = 10
private const val SOURCE_TIMEOUT_MS = 12_000L

class AlternativesUseCase @Inject constructor(
	private val sourcesRepository: MangaSourcesRepository,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend operator fun invoke(manga: Manga, throughDisabledSources: Boolean): Flow<Manga> {
		val sources = getSources(manga.source, throughDisabledSources)
			.filter { isSearchSupported(it) }
			.take(MAX_SOURCES)
		if (sources.isEmpty()) {
			return emptyFlow()
		}
		val resultCount = AtomicInteger(0)
		val searchSemaphore = Semaphore(MAX_SEARCH_PARALLELISM)
		return channelFlow {
			val seen = HashSet<MangaStoredEntryKey>()
			fun markSeen(key: MangaStoredEntryKey): Boolean = synchronized(seen) {
				seen.add(key)
			}
			for (source in sources) {
				launch {
					if (resultCount.get() >= MAX_TOTAL_RESULTS) {
						return@launch
					}
					val searchHelper = searchHelperFactory.create(source)
					val list = runCatchingCancellable {
						searchSemaphore.withPermit {
							withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
								searchHelper(manga.title, SearchKind.TITLE)?.manga
							}
						}
					}.getOrNull() ?: return@launch
					for (candidate in list.take(MAX_CANDIDATES_PER_SOURCE)) {
						if (resultCount.get() >= MAX_TOTAL_RESULTS) {
							break
						}
						val key = candidate.storedEntryKey()
						if (!markSeen(key)) {
							continue
						}
						send(candidate)
						resultCount.incrementAndGet()
					}
				}
			}
		}
	}

	private fun Manga.storedEntryKey(): MangaStoredEntryKey {
		val sourceName = source.unwrap().name
		val value = if (id != 0L) {
			"id:$id"
		} else {
			"url:$url\n$publicUrl"
		}
		return MangaStoredEntryKey(sourceName, value)
	}

	private suspend fun isSearchSupported(source: MangaSource): Boolean = runCatchingCancellable {
		mangaRepositoryFactory.create(source).filterCapabilities.isSearchSupported
	}.getOrDefault(false)

	private suspend fun getSources(ref: MangaSource, disabled: Boolean): List<MangaSource> = buildList {
		val refSource = ref.unwrap()
		if (!disabled) {
			add(refSource)
		}
		val sources = if (disabled) {
			sourcesRepository.getDisabledSources()
		} else {
			sourcesRepository.getEnabledSources()
		}
		for (source in sources) {
			val unwrapped = source.unwrap()
			add(unwrapped)
		}
	}.distinctBy { it.unwrap().name }
		.sortedByDescending { it.priority(ref) }

	private fun MangaSource.priority(ref: MangaSource): Int {
		var res = 0
		val source = unwrap()
		val refSource = ref.unwrap()
		if (source.identityName() == refSource.identityName()) {
			res += 8
		}
		if (source is MangaParserSource && refSource is MangaParserSource) {
			if (source.locale == refSource.locale) {
				res += 4
			} else if (source.locale.toLocale() == Locale.getDefault()) {
				res += 2
			}
			if (source.contentType == refSource.contentType) {
				res++
			}
		}
		return res
	}

	private data class MangaStoredEntryKey(
		val sourceName: String,
		val value: String,
	)
}
