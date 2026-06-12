package com.kosen.reader.core.image

import coil3.intercept.Interceptor
import coil3.network.httpHeaders
import coil3.request.ImageResult
import com.kosen.reader.core.model.PluginMangaSource
import com.kosen.reader.core.model.unwrap
import com.kosen.reader.core.network.CommonHeaders
import com.kosen.reader.core.util.ext.mangaSourceKey
import com.kosen.reader.parsers.model.MangaParserSource

class MangaSourceHeaderInterceptor : Interceptor {

	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val mangaSource = chain.request.extras[mangaSourceKey]?.unwrap()
		val sourceName = when (mangaSource) {
			is MangaParserSource -> mangaSource.name
			is PluginMangaSource -> mangaSource.name
			else -> return chain.proceed()
		}
		val request = chain.request
		val newHeaders = request.httpHeaders.newBuilder()
			.set(CommonHeaders.MANGA_SOURCE, sourceName)
			.build()
		val newRequest = request.newBuilder()
			.httpHeaders(newHeaders)
			.build()
		return chain.withRequest(newRequest).proceed()
	}
}
