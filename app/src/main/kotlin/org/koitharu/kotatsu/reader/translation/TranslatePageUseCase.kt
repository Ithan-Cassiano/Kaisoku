package org.koitharu.kotatsu.reader.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.core.util.ext.isZipUri
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.reader.domain.PageLoader
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class TranslatePageUseCase @Inject constructor(
	private val pageLoader: PageLoader,
	private val pageTranslationSettings: PageTranslationSettings,
	private val textRecognizer: MlKitPageTextRecognizer,
	private val remotePageTextTranslator: RemotePageTextTranslator,
	private val pageTranslationRenderer: PageTranslationRenderer,
) {

	suspend operator fun invoke(page: MangaPage): Int {
		val config = pageTranslationSettings.getConfig()
		check(config.isConfigured()) { "Page translation is not configured" }
		val traceId = buildTraceId(page)
		val requestStartedAt = SystemClock.elapsedRealtime()
		Log.d(
			TAG,
			"[$traceId] Starting translation provider=${config.provider.name} ocrMode=${config.ocrMode.name} target=${config.targetLanguage}",
		)
		val pageUrl = withTimeoutOrThrow(PAGE_URL_TIMEOUT_MS, "Resolving page", traceId) {
			pageLoader.getPageUrl(page)
		}
		Log.d(TAG, "[$traceId] Resolved page url host=${Uri.parse(pageUrl).host ?: "unknown"}")
		val sourceUri = withTimeoutOrThrow(PAGE_LOAD_TIMEOUT_MS, "Loading page", traceId) {
			pageLoader.loadOriginalPage(page, pageUrlOverride = pageUrl)
		}
		val resolvedUri = if (sourceUri.isZipUri()) {
			pageLoader.convertBimap(sourceUri)
		} else {
			sourceUri
		}
		val sourceBitmap = loadBitmap(resolvedUri)
		try {
			Log.d(
				TAG,
				"[$traceId] Loaded bitmap ${sourceBitmap.width}x${sourceBitmap.height} from ${resolvedUri.scheme ?: "unknown"}",
			)
			val blocks = recognizeBlocks(
				traceId = traceId,
				sourceBitmap = sourceBitmap,
				mode = config.ocrMode,
			)
			if (blocks.isEmpty()) {
				Log.d(TAG, "[$traceId] No OCR blocks detected")
				return 0
			}
			val translations = withTimeoutOrThrow(
				timeoutMs = TRANSLATION_TIMEOUT_MS,
				stage = "Translating page",
				traceId = traceId,
			) {
				remotePageTextTranslator.translate(
					blocks = blocks,
					config = config,
					traceId = traceId,
				)
			}
			Log.d(TAG, "[$traceId] Received ${translations.size} translations")
			val renderedBitmap = pageTranslationRenderer.render(sourceBitmap, blocks, translations)
			try {
				Log.d(TAG, "[$traceId] Rendering complete, saving translated page")
				withTimeoutOrThrow(
					timeoutMs = CACHE_WRITE_TIMEOUT_MS,
					stage = "Saving translated page",
					traceId = traceId,
				) {
					pageLoader.registerTranslatedPage(
						page = page,
						bitmap = renderedBitmap,
						pageUrlOverride = pageUrl,
					)
				}
			} finally {
				if (renderedBitmap !== sourceBitmap && !renderedBitmap.isRecycled) {
					renderedBitmap.recycle()
				}
			}
			Log.d(
				TAG,
				"[$traceId] Translation finished in ${SystemClock.elapsedRealtime() - requestStartedAt}ms blocks=${blocks.size}",
			)
			return blocks.size
		} finally {
			if (!sourceBitmap.isRecycled) {
				sourceBitmap.recycle()
			}
		}
	}

	private suspend fun loadBitmap(uri: android.net.Uri): Bitmap = runInterruptible(Dispatchers.IO) {
		val file = uri.toFile()
		val options = BitmapFactory.Options().apply {
			inMutable = true
			inPreferredConfig = Bitmap.Config.ARGB_8888
		}
		BitmapFactory.decodeFile(file.absolutePath, options)
			?: throw IOException("Cannot decode image: ${file.absolutePath}")
	}

	private suspend fun recognizeBlocks(
		traceId: String,
		sourceBitmap: Bitmap,
		mode: PageTranslationOcrMode,
	): List<PageTranslationBlock> {
		val ocrBitmap = sourceBitmap.createOcrBitmap()
		try {
			Log.d(
				TAG,
				"[$traceId] Starting OCR mode=${mode.name} bitmap=${ocrBitmap.width}x${ocrBitmap.height}",
			)
			val blocks = withTimeoutOrThrow(
				timeoutMs = OCR_TIMEOUT_MS,
				stage = "Recognizing text",
				traceId = traceId,
			) {
				textRecognizer.recognize(ocrBitmap, mode)
			}
			Log.d(TAG, "[$traceId] OCR finished with ${blocks.size} blocks")
			if (ocrBitmap === sourceBitmap) {
				return blocks
			}
			val scaleX = sourceBitmap.width / ocrBitmap.width.toFloat()
			val scaleY = sourceBitmap.height / ocrBitmap.height.toFloat()
			return blocks.map { block ->
				block.copy(
					bounds = Rect(
						(block.bounds.left * scaleX).roundToInt().coerceIn(0, sourceBitmap.width - 1),
						(block.bounds.top * scaleY).roundToInt().coerceIn(0, sourceBitmap.height - 1),
						(block.bounds.right * scaleX).roundToInt().coerceIn(1, sourceBitmap.width),
						(block.bounds.bottom * scaleY).roundToInt().coerceIn(1, sourceBitmap.height),
					),
				)
			}
		} finally {
			if (ocrBitmap !== sourceBitmap && !ocrBitmap.isRecycled) {
				ocrBitmap.recycle()
			}
		}
	}

	private fun Bitmap.createOcrBitmap(): Bitmap {
		val maxDimension = max(width, height)
		if (maxDimension <= OCR_MAX_DIMENSION) {
			return this
		}
		val scale = OCR_MAX_DIMENSION / maxDimension.toFloat()
		val scaledWidth = max(1, (width * scale).roundToInt())
		val scaledHeight = max(1, (height * scale).roundToInt())
		return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
	}

	private suspend fun <T> withTimeoutOrThrow(
		timeoutMs: Long,
		stage: String,
		traceId: String,
		block: suspend () -> T,
	): T {
		val startedAt = SystemClock.elapsedRealtime()
		Log.d(TAG, "[$traceId] $stage started timeout=${timeoutMs}ms")
		return try {
			withTimeout(timeoutMs) {
				block()
			}.also {
				Log.d(TAG, "[$traceId] $stage finished in ${SystemClock.elapsedRealtime() - startedAt}ms")
			}
		} catch (e: TimeoutCancellationException) {
			Log.w(TAG, "[$traceId] $stage timed out after ${timeoutMs}ms", e)
			throw SocketTimeoutException("$stage timed out").apply {
				initCause(e)
			}
		} catch (e: Throwable) {
			Log.e(TAG, "[$traceId] $stage failed", e)
			throw e
		}
	}

	private fun buildTraceId(page: MangaPage): String {
		return "${page.id}:${page.source.name}:${page.url.hashCode().toUInt().toString(16)}"
	}

	private companion object {
		private const val TAG = "PageTranslation"
		const val PAGE_URL_TIMEOUT_MS = 20_000L
		const val PAGE_LOAD_TIMEOUT_MS = 20_000L
		const val OCR_TIMEOUT_MS = 45_000L
		const val TRANSLATION_TIMEOUT_MS = 90_000L
		const val CACHE_WRITE_TIMEOUT_MS = 15_000L
		const val OCR_MAX_DIMENSION = 2048
	}
}
