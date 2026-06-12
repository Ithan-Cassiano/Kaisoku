package com.kosen.reader.core.parser

import com.kosen.reader.core.cache.MemoryContentCache
import com.kosen.reader.core.model.TestMangaSource
import com.kosen.reader.parsers.MangaLoaderContext

@Suppress("unused")
class TestMangaRepository(
	private val loaderContext: MangaLoaderContext,
	cache: MemoryContentCache
) : EmptyMangaRepository(TestMangaSource)
