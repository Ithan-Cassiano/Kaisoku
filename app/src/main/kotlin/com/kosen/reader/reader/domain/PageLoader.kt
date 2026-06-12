package com.kosen.reader.reader.domain

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import androidx.annotation.AnyThread
import androidx.annotation.CheckResult
import androidx.collection.LongSparseArray
import androidx.collection.set
import androidx.core.net.toFile
import androidx.core.net.toUri
import coil3.BitmapImage
import coil3.Image
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.toBitmap
import com.davemorrissey.labs.subscaleview.ImageSource
import dagger.hilt.android.ActivityRetainedLifecycle
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.source
import okio.use
import org.jetbrains.annotations.Blocking
import com.kosen.reader.core.LocalizedAppContext
import com.kosen.reader.core.image.BitmapDecoderCompat
import com.kosen.reader.core.image.InkStoryImageDecoder
import com.kosen.reader.core.network.CommonHeaders
import com.kosen.reader.core.network.MangaHttpClient
import com.kosen.reader.core.network.imageproxy.ImageProxyInterceptor
import com.kosen.reader.core.parser.CachingMangaRepository
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.image.TrimTransformation
import com.kosen.reader.core.util.FileSize
import com.kosen.reader.core.util.MimeTypes
import com.kosen.reader.core.util.ext.URI_SCHEME_ZIP
import com.kosen.reader.core.util.ext.cancelChildrenAndJoin
import com.kosen.reader.core.util.ext.compressToPNG
import com.kosen.reader.core.util.ext.ensureRamAtLeast
import com.kosen.reader.core.util.ext.ensureSuccess
import com.kosen.reader.core.util.ext.getCompletionResultOrNull
import com.kosen.reader.core.util.ext.isFileUri
import com.kosen.reader.core.util.ext.isImage
import com.kosen.reader.core.util.ext.isNotEmpty
import com.kosen.reader.core.util.ext.isPowerSaveMode
import com.kosen.reader.core.util.ext.isZipUri
import com.kosen.reader.core.util.ext.lifecycleScope
import com.kosen.reader.core.util.ext.mangaSourceExtra
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.ramAvailable
import com.kosen.reader.core.util.ext.toMimeType
import com.kosen.reader.core.util.ext.use
import com.kosen.reader.core.util.ext.withProgress
import com.kosen.reader.core.util.progress.ProgressDeferred
import com.kosen.reader.download.ui.worker.DownloadSlowdownDispatcher
import com.kosen.reader.local.data.LocalStorageCache
import com.kosen.reader.local.data.PageCache
import com.kosen.reader.parsers.model.MangaPage
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.util.requireBody
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.reader.ui.pager.ReaderPage
import java.io.File
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

@ActivityRetainedScoped
class PageLoader @Inject constructor(
	@LocalizedAppContext private val context: Context,
	lifecycle: ActivityRetainedLifecycle,
	@MangaHttpClient private val okHttp: OkHttpClient,
	@PageCache private val cache: LocalStorageCache,
	private val coil: ImageLoader,
	private val settings: AppSettings,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val imageProxyInterceptor: ImageProxyInterceptor,
	private val downloadSlowdownDispatcher: DownloadSlowdownDispatcher,
) {

	val loaderScope = lifecycle.lifecycleScope + InternalErrorHandler() + Dispatchers.Default

	private val tasks = LongSparseArray<ProgressDeferred<Uri, Float>>()
	private val semaphore = Semaphore(3)
	private val convertLock = Mutex()
	private val prefetchLock = Mutex()

	@Volatile
	private var repository: MangaRepository? = null
	private val prefetchQueue = LinkedList<MangaPage>()
	private val counter = AtomicInteger(0)
	private var prefetchQueueLimit = PREFETCH_LIMIT_DEFAULT // TODO adaptive
	private val edgeDetector = EdgeDetector(context)

	fun isPrefetchApplicable(): Boolean {
		return repository is CachingMangaRepository
			&& settings.isPagesPreloadEnabled
			&& !context.isPowerSaveMode()
			&& !isLowRam()
	}

	@AnyThread
	fun prefetch(pages: List<ReaderPage>) = loaderScope.launch {
		prefetchLock.withLock {
			for (page in pages.asReversed()) {
				if (tasks.containsKey(page.id)) {
					continue
				}
				prefetchQueue.offerFirst(page.toMangaPage())
				if (prefetchQueue.size > prefetchQueueLimit) {
					prefetchQueue.pollLast()
				}
			}
		}
		if (counter.get() == 0) {
			onIdle()
		}
	}

	suspend fun loadPreview(page: MangaPage): ImageSource? {
		val preview = page.preview
		if (preview.isNullOrEmpty()) {
			return null
		}
		val request = ImageRequest.Builder(context)
			.data(preview)
			.mangaSourceExtra(page.source)
			.transformations(TrimTransformation())
			.build()
		return coil.execute(request).image?.toImageSource()
	}

	fun peekPreviewSource(preview: String?): ImageSource? {
		if (preview.isNullOrEmpty()) {
			return null
		}
		coil.memoryCache?.let { cache ->
			val key = MemoryCache.Key(preview)
			cache[key]?.image?.let {
				return if (it is BitmapImage) {
					ImageSource.cachedBitmap(it.toBitmap())
				} else {
					ImageSource.bitmap(it.toBitmap())
				}
			}
		}
		coil.diskCache?.let { cache ->
			cache.openSnapshot(preview)?.use { snapshot ->
				return ImageSource.file(snapshot.data.toFile())
			}
		}
		return null
	}

	fun loadPageAsync(page: MangaPage, force: Boolean): ProgressDeferred<Uri, Float> {
		var task = tasks[page.id]?.takeIf { it.isValid() }
		if (force) {
			task?.cancel()
		} else if (task?.isCancelled == false) {
			return task
		}
		task = loadPageAsyncImpl(page, skipCache = force, isPrefetch = false)
		synchronized(tasks) {
			tasks[page.id] = task
		}
		return task
	}

	suspend fun loadPage(page: MangaPage, force: Boolean): Uri {
		return loadPageAsync(page, force).await()
	}

	@CheckResult
	suspend fun convertBimap(uri: Uri): Uri = convertLock.withLock {
		if (uri.isZipUri()) {
			runInterruptible(Dispatchers.IO) {
				ZipFile(uri.schemeSpecificPart).use { zip ->
					val entry = zip.getEntry(uri.fragment)
					context.ensureRamAtLeast(entry.size * 2)
					val bytes = zip.getInputStream(entry).use { it.readBytes() }
					val payload = InkStoryImageDecoder.resolveLocalPayload(bytes)
					val sourceBytes = payload?.bytes ?: bytes
					val mimeType = payload?.mimeType
						?: BitmapDecoderCompat.probeMimeType(bytes)
						?: MimeTypes.getMimeTypeFromExtension(entry.name)
					BitmapDecoderCompat.decode(sourceBytes.inputStream(), mimeType)
				}
			}.use { image ->
				cache.set(uri.toString(), image).toUri()
			}
		} else {
			val file = uri.toFile()
			runInterruptible(Dispatchers.IO) {
				context.ensureRamAtLeast(file.length() * 2)
				val bytes = file.readBytes()
				val payload = InkStoryImageDecoder.resolveLocalPayload(bytes)
				val sourceBytes = payload?.bytes ?: bytes
				val mimeType = payload?.mimeType
					?: BitmapDecoderCompat.probeMimeType(bytes)
					?: BitmapDecoderCompat.probeMimeType(file)
				BitmapDecoderCompat.decode(sourceBytes.inputStream(), mimeType)
			}.use { image ->
				image.compressToPNG(file)
			}
			uri
		}
	}

	suspend fun getTrimmedBounds(uri: Uri): Rect? = runCatchingCancellable {
		edgeDetector.getBounds(ImageSource.uri(uri))
	}.onFailure { error ->
		error.printStackTraceDebug()
	}.getOrNull()

	suspend fun getPageUrl(page: MangaPage): String {
		return getRepository(page.source).getPageUrl(page)
	}

	suspend fun invalidate(clearCache: Boolean) {
		tasks.clear()
		loaderScope.cancelChildrenAndJoin()
		if (clearCache) {
			cache.clear()
		}
	}

	private fun onIdle() = loaderScope.launch {
		prefetchLock.withLock {
			while (prefetchQueue.isNotEmpty()) {
				val page = prefetchQueue.pollFirst() ?: return@launch
				synchronized(tasks) {
					tasks[page.id] = loadPageAsyncImpl(page, skipCache = false, isPrefetch = true)
				}
			}
		}
	}

	private fun loadPageAsyncImpl(
		page: MangaPage,
		skipCache: Boolean,
		isPrefetch: Boolean,
	): ProgressDeferred<Uri, Float> {
		val progress = MutableStateFlow(PROGRESS_UNDEFINED)
		val deferred = loaderScope.async {
			counter.incrementAndGet()
			try {
				loadPageImpl(
					page = page,
					progress = progress,
					isPrefetch = isPrefetch,
					skipCache = skipCache,
				)
			} finally {
				if (counter.decrementAndGet() == 0) {
					onIdle()
				}
			}
		}
		return ProgressDeferred(deferred, progress)
	}

	@Synchronized
	private fun getRepository(source: MangaSource): MangaRepository {
		val result = repository
		return if (result != null && result.source == source) {
			result
		} else {
			mangaRepositoryFactory.create(source).also { repository = it }
		}
	}

	private suspend fun loadPageImpl(
		page: MangaPage,
		progress: MutableStateFlow<Float>,
		isPrefetch: Boolean,
		skipCache: Boolean,
	): Uri = semaphore.withPermit {
		val repo = getRepository(page.source)
		val pageUrl = getPageUrl(page)
		check(pageUrl.isNotBlank()) { "Cannot obtain full image url for $page" }
		if (!skipCache) {
			cache.get(pageUrl)?.let { cachedFile ->
				if (shouldValidateInkStoryCache(pageUrl, page.source) && !isCachedImageFileValid(cachedFile)) {
					runInterruptible(Dispatchers.IO) {
						cachedFile.delete()
					}
				} else {
					return cachedFile.toUri()
				}
			}
		}
		val uri = pageUrl.toUri()
		return when {
			uri.isZipUri() -> if (uri.scheme == URI_SCHEME_ZIP) {
				uri
			} else { // legacy uri
				uri.buildUpon().scheme(URI_SCHEME_ZIP).build()
			}

			uri.isFileUri() -> uri
			else -> {
				if (isPrefetch) {
					downloadSlowdownDispatcher.delay(page.source)
				}
				val request = repo.createPageRequest(pageUrl, page)
				val httpClient = repo.getImageClient() ?: okHttp
				imageProxyInterceptor.interceptPageRequest(request, httpClient).ensureSuccess().use { response ->
					response.requireBody().withProgress(progress).use {
						val responseMimeType = it.contentType()?.toMimeType()
						if (InkStoryImageDecoder.isInkStorySource(page.source)) {
							val payload = InkStoryImageDecoder.resolveNetworkPayload(
								bytes = it.bytes(),
								responseMimeType = responseMimeType,
								pageUrl = pageUrl,
								mangaSource = page.source,
							) ?: throw IllegalStateException("InkStory payload is not a supported image")
							payload.bytes.inputStream().source().use { decodedSource ->
								cache.set(pageUrl, decodedSource, payload.mimeType)
							}
						} else {
							val mimeType = responseMimeType?.takeIf { mime -> mime.isImage }
							cache.set(pageUrl, it.source(), mimeType)
						}
					}
				}.toUri()
			}
		}
	}

	private suspend fun isCachedImageFileValid(file: File): Boolean = runInterruptible(Dispatchers.IO) {
		MimeTypes.probeMimeType(file)?.isImage == true
	}

	private fun isLowRam(): Boolean {
		return context.ramAvailable <= FileSize.MEGABYTES.convert(PREFETCH_MIN_RAM_MB, FileSize.BYTES)
	}

	private fun Image.toImageSource(): ImageSource = if (this is BitmapImage) {
		ImageSource.cachedBitmap(toBitmap())
	} else {
		ImageSource.bitmap(toBitmap())
	}

	private fun Deferred<Uri>.isValid(): Boolean {
		return getCompletionResultOrNull()?.map { uri ->
			uri.exists() && uri.isTargetNotEmpty()
		}?.getOrDefault(false) != false
	}

	private class InternalErrorHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler),
		CoroutineExceptionHandler {

		override fun handleException(context: CoroutineContext, exception: Throwable) {
			exception.printStackTraceDebug()
		}
	}

	companion object {

		private const val PROGRESS_UNDEFINED = -1f
		private const val PREFETCH_LIMIT_DEFAULT = 6
		private const val PREFETCH_MIN_RAM_MB = 80L

		private fun shouldValidateInkStoryCache(pageUrl: String, mangaSource: MangaSource): Boolean {
			return InkStoryImageDecoder.shouldValidateCachedFile(pageUrl, mangaSource)
		}


		@Blocking
		private fun Uri.exists(): Boolean = when {
			isFileUri() -> toFile().exists()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { it.getEntry(fragment) != null }
			}

			else -> false
		}

		@Blocking
		private fun Uri.isTargetNotEmpty(): Boolean = when {
			isFileUri() -> toFile().isNotEmpty()
			isZipUri() -> {
				val file = File(requireNotNull(schemeSpecificPart))
				file.exists() && ZipFile(file).use { (it.getEntry(fragment)?.size ?: 0L) != 0L }
			}

			else -> false
		}
	}
}
