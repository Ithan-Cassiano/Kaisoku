package com.kosen.reader.core.parser.favicon

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import coil3.ColorImage
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import coil3.toAndroidUri
import coil3.toBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import okio.FileSystem
import okio.IOException
import okio.Path.Companion.toOkioPath
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.CloudFlareProtectedException
import com.kosen.reader.core.model.MangaSource
import com.kosen.reader.core.parser.EmptyMangaRepository
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.parser.ParserMangaRepository
import com.kosen.reader.core.parser.PluginMangaRepository
import com.kosen.reader.core.parser.external.ExternalMangaRepository
import com.kosen.reader.core.util.MimeTypes
import com.kosen.reader.core.util.ext.faviconCacheOnlyKey
import com.kosen.reader.core.util.ext.fetch
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.toMimeTypeOrNull
import com.kosen.reader.local.data.FaviconCache
import com.kosen.reader.local.data.LocalMangaRepository
import com.kosen.reader.local.data.LocalStorageCache
import com.kosen.reader.parsers.util.runCatchingCancellable
import java.io.File
import javax.inject.Inject
import coil3.Uri as CoilUri

class FaviconFetcher(
	private val uri: Uri,
	private val options: Options,
	private val imageLoader: ImageLoader,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val localStorageCache: LocalStorageCache,
) : Fetcher {

	override suspend fun fetch(): FetchResult? {
		val mangaSource = MangaSource(uri.schemeSpecificPart)
		val isCacheOnly = options.extras[faviconCacheOnlyKey] == true

		return when (val repo = mangaRepositoryFactory.create(mangaSource)) {
			is ParserMangaRepository -> fetchParserFavicon(repo)
			is PluginMangaRepository -> fetchPluginParserFavicon(repo)
			is ExternalMangaRepository -> fetchPluginIcon(repo)
			is EmptyMangaRepository -> ImageFetchResult(
				image = ColorImage(Color.WHITE),
				isSampled = false,
				dataSource = DataSource.MEMORY,
			)

			is LocalMangaRepository -> imageLoader.fetch(R.drawable.ic_storage, options)

			else -> if (isCacheOnly) {
				throw NoSuchElementException("No cached favicon for ${repo.javaClass.simpleName}")
			} else {
				throw IllegalArgumentException("Unsupported repo ${repo.javaClass.simpleName}")
			}
		}
	}

	private suspend fun fetchParserFavicon(repository: ParserMangaRepository): FetchResult {
		return fetchParserFavicons(repository.source.name) {
			repository.getFavicons()
		}
	}

	private suspend fun fetchPluginParserFavicon(repository: PluginMangaRepository): FetchResult {
		return fetchParserFavicons(repository.source.name) {
			repository.getFavicons()
		}
	}

	private suspend fun fetchParserFavicons(
		sourceName: String,
		getFavicons: suspend () -> com.kosen.reader.parsers.model.Favicons,
	): FetchResult {
		val sizePx = maxOf(
			options.size.width.pxOrElse { FALLBACK_SIZE },
			options.size.height.pxOrElse { FALLBACK_SIZE },
		)
		val cacheKey = options.diskCacheKey ?: "${sourceName}_$sizePx"
		if (options.diskCachePolicy.readEnabled) {
			localStorageCache[cacheKey]?.let { file ->
				return SourceFetchResult(
					source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
					mimeType = MimeTypes.probeMimeType(file)?.toString(),
					dataSource = DataSource.DISK,
				)
			}
		}
		if (options.extras[faviconCacheOnlyKey] == true) {
			throw NoSuchElementException("No cached favicon for $sourceName")
		}
		var favicons = getFavicons()
		var lastError: Exception? = null
		while (favicons.isNotEmpty()) {
			currentCoroutineContext().ensureActive()
			val icon = favicons.find(sizePx) ?: throwNSEE(lastError)
			try {
				val result = imageLoader.fetch(icon.url, options)
				if (result != null) {
					return if (options.diskCachePolicy.writeEnabled) {
						writeToCache(cacheKey, result)
					} else {
						result
					}
				} else {
					favicons -= icon
				}
			} catch (e: CloudFlareProtectedException) {
				throw e
			} catch (e: IOException) {
				lastError = e
				favicons -= icon
			}
		}
		throwNSEE(lastError)
	}

	private suspend fun fetchPluginIcon(repository: ExternalMangaRepository): FetchResult {
		val source = repository.source
		val pm = options.context.packageManager
		val icon = runInterruptible {
			val provider = pm.resolveContentProvider(source.authority, 0)
			provider?.loadIcon(pm) ?: pm.getApplicationIcon(source.packageName)
		}
		return ImageFetchResult(
			image = icon.nonAdaptive().asImage(),
			isSampled = false,
			dataSource = DataSource.DISK,
		)
	}

	private suspend fun writeToCache(key: String, result: FetchResult): FetchResult = runCatchingCancellable {
		when (result) {
			is ImageFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					localStorageCache.set(key, result.image.toBitmap()).asFetchResult()
				} else {
					result
				}
			}

			is SourceFetchResult -> {
				if (result.dataSource == DataSource.NETWORK) {
					result.source.source().use {
						localStorageCache.set(key, it, result.mimeType?.toMimeTypeOrNull()).asFetchResult()
					}
				} else {
					result
				}
			}
		}
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrDefault(result)

	private fun File.asFetchResult() = SourceFetchResult(
		source = ImageSource(toOkioPath(), FileSystem.SYSTEM),
		mimeType = MimeTypes.probeMimeType(this)?.toString(),
		dataSource = DataSource.DISK,
	)

	class Factory @Inject constructor(
		private val mangaRepositoryFactory: MangaRepository.Factory,
		@FaviconCache private val faviconCache: LocalStorageCache,
	) : Fetcher.Factory<CoilUri> {

		override fun create(
			data: CoilUri,
			options: Options,
			imageLoader: ImageLoader
		): Fetcher? = if (data.scheme == URI_SCHEME_FAVICON) {
			FaviconFetcher(data.toAndroidUri(), options, imageLoader, mangaRepositoryFactory, faviconCache)
		} else {
			null
		}
	}

	private companion object {

		const val FALLBACK_SIZE = 9999 // largest icon

		private fun throwNSEE(lastError: Exception?): Nothing {
			if (lastError != null) {
				throw lastError
			} else {
				throw NoSuchElementException("No favicons found")
			}
		}

		private fun Drawable.nonAdaptive() =
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
				LayerDrawable(arrayOf(background, foreground))
			} else {
				this
			}

	}
}
