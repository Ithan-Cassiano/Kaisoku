package com.kosen.reader.core.parser

import android.content.Context
import androidx.annotation.AnyThread
import androidx.collection.ArrayMap
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.kosen.reader.core.cache.MemoryContentCache
import com.kosen.reader.core.model.LocalMangaSource
import com.kosen.reader.core.model.MangaSourceInfo
import com.kosen.reader.core.model.PluginMangaSource
import com.kosen.reader.core.model.TestMangaSource
import com.kosen.reader.core.model.UnknownMangaSource
import com.kosen.reader.core.prefs.SourceSettings
import com.kosen.reader.core.network.CommonHeaders
import com.kosen.reader.core.parser.external.ExternalMangaRepository
import com.kosen.reader.core.parser.external.ExternalMangaSource
import com.kosen.reader.core.parser.mihon.MihonExtensionManager
import com.kosen.reader.core.parser.mihon.MihonMangaRepository
import com.kosen.reader.core.parser.mihon.MihonMangaSource
import com.kosen.reader.local.data.LocalMangaRepository
import com.kosen.reader.parsers.MangaLoaderContext
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaChapter
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaListFilterCapabilities
import com.kosen.reader.parsers.model.MangaListFilterOptions
import com.kosen.reader.parsers.model.MangaPage
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.SortOrder
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

interface MangaRepository {

	val source: MangaSource

	val sortOrders: Set<SortOrder>

	var defaultSortOrder: SortOrder

	val filterCapabilities: MangaListFilterCapabilities

	suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga>

	suspend fun getDetails(manga: Manga): Manga

	suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	suspend fun getPageUrl(page: MangaPage): String

	suspend fun getFilterOptions(): MangaListFilterOptions

	suspend fun getRelated(seed: Manga): List<Manga>

	fun getImageClient(): OkHttpClient? = null

	fun createPageRequest(pageUrl: String, page: MangaPage): Request = Request.Builder()
		.url(pageUrl)
		.get()
		.header(CommonHeaders.ACCEPT, "image/webp,image/png;q=0.9,image/jpeg,*/*;q=0.8")
		.cacheControl(CommonHeaders.CACHE_CONTROL_NO_STORE)
		.tag(MangaSource::class.java, page.source)
		.build()

	suspend fun find(manga: Manga): Manga? {
		val list = getList(0, SortOrder.RELEVANCE, MangaListFilter(query = manga.title))
		return list.find { x -> x.id == manga.id }
	}

	@Singleton
	class Factory @Inject constructor(
		@ApplicationContext private val context: Context,
		private val localMangaRepository: LocalMangaRepository,
		private val loaderContext: MangaLoaderContext,
		private val contentCache: MemoryContentCache,
		private val mirrorSwitcher: MirrorSwitcher,
		private val mihonExtensionManager: MihonExtensionManager,
	) {

		private val cache = ArrayMap<MangaSource, WeakReference<MangaRepository>>()

		@AnyThread
		fun create(source: MangaSource): MangaRepository {
			when (source) {
				is MangaSourceInfo -> return create(source.mangaSource)
				LocalMangaSource -> return localMangaRepository
				UnknownMangaSource -> return EmptyMangaRepository(source)
			}
			cache[source]?.get()?.let { return it }
			return synchronized(cache) {
				cache[source]?.get()?.let { return it }
				val repository = createRepository(source)
				if (repository != null) {
					cache[source] = WeakReference(repository)
					repository
				} else {
					EmptyMangaRepository(source)
				}
			}
		}

		private fun createRepository(source: MangaSource): MangaRepository? = when (source) {
			is MangaParserSource -> ParserMangaRepository(
				parser = loaderContext.newParserInstance(source),
				cache = contentCache,
				mirrorSwitcher = mirrorSwitcher,
			)

			is PluginMangaSource -> PluginMangaRepository(
				loadedParser = DynamicParserManager.createParser(source, loaderContext, context),
				settings = SourceSettings(context, source),
				cache = contentCache,
			)

			TestMangaSource -> TestMangaRepository(
				loaderContext = loaderContext,
				cache = contentCache,
			)

			is ExternalMangaSource -> if (source.isAvailable(context)) {
				ExternalMangaRepository(
					contentResolver = context.contentResolver,
					source = source,
					cache = contentCache,
				)
			} else {
				EmptyMangaRepository(source)
			}

			is MihonMangaSource -> mihonExtensionManager.resolve(source)?.let {
				MihonMangaRepository(
					loadedSource = it,
					cache = contentCache,
				)
			} ?: EmptyMangaRepository(source)

			else -> null
		}
	}
}
