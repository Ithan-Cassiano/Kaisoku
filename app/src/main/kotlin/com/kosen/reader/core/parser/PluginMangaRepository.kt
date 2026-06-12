package com.kosen.reader.core.parser

import kotlinx.coroutines.Dispatchers
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import com.kosen.reader.core.cache.MemoryContentCache
import com.kosen.reader.core.model.PluginMangaSource
import com.kosen.reader.core.prefs.SourceSettings
import com.kosen.reader.parsers.MangaParserAuthProvider
import com.kosen.reader.parsers.config.ConfigKey
import com.kosen.reader.parsers.model.Favicons
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaChapter
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaListFilterCapabilities
import com.kosen.reader.parsers.model.MangaListFilterOptions
import com.kosen.reader.parsers.model.MangaPage
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.suspendlazy.suspendLazy

class PluginMangaRepository(
	private val loadedParser: DynamicParserManager.LoadedParser,
	private val settings: SourceSettings,
	cache: MemoryContentCache,
) : CachingMangaRepository(cache), Interceptor {

	private val delegate: Any
		get() = loadedParser.delegate

	private val filterOptionsLazy = suspendLazy(Dispatchers.Default) {
		callSuspend<MangaListFilterOptions>("getFilterOptions")
	}

	override val source: PluginMangaSource
		get() = loadedParser.source

	override val sortOrders: Set<SortOrder>
		get() = call("getAvailableSortOrders") ?: setOf(SortOrder.UPDATED)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = call("getFilterCapabilities") ?: MangaListFilterCapabilities()

	override var defaultSortOrder: SortOrder
		get() = settings.defaultSortOrder?.takeIf { it in sortOrders } ?: sortOrders.first()
		set(value) {
			settings.defaultSortOrder = value
		}

	val domain: String
		get() = call<String>("getDomain").orEmpty()

	val domains: Array<out String>
		get() = configKeyDomain?.presetValues ?: emptyArray()

	val configKeyDomain: ConfigKey.Domain?
		get() = call("getConfigKeyDomain")

	override fun intercept(chain: Interceptor.Chain): Response {
		return call("intercept", chain) ?: chain.proceed(chain.request())
	}

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		return callSuspend(
			"getList",
			offset,
			order ?: defaultSortOrder,
			filter ?: MangaListFilter.EMPTY,
		)
	}

	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> =
		callSuspend("getPages", chapter)

	override suspend fun getPageUrl(page: MangaPage): String =
		callSuspend<String>("getPageUrl", page).also { result ->
			check(result.isNotEmpty()) { "Page url is empty" }
		}

	override suspend fun getFilterOptions(): MangaListFilterOptions = filterOptionsLazy.get()

	suspend fun getFavicons(): Favicons = callSuspend("getFavicons")

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> =
		callSuspend("getRelatedManga", seed)

	override suspend fun getDetailsImpl(manga: Manga): Manga =
		callSuspend("getDetails", manga)

	fun getAuthProvider(): MangaParserAuthProvider? = delegate as? MangaParserAuthProvider

	fun getRequestHeaders(): Headers = call("getRequestHeaders") ?: Headers.Builder().build()

	fun getConfigKeys(): List<ConfigKey<*>> = ArrayList<ConfigKey<*>>().also {
		DynamicParserManager.invoke(delegate, "onCreateConfig", it)
	}

	fun getConfig(): SourceSettings = settings

	@Suppress("UNCHECKED_CAST")
	private fun <T> call(name: String, vararg args: Any?): T? =
		DynamicParserManager.invoke(delegate, name, *args) as? T

	@Suppress("UNCHECKED_CAST")
	private suspend fun <T> callSuspend(name: String, vararg args: Any?): T =
		DynamicParserManager.invokeSuspend(delegate, name, *args) as T
}
