package com.kosen.reader.explore.domain

import com.kosen.reader.core.model.MangaSourceInfo
import com.kosen.reader.core.model.PluginMangaSource
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaSource
import java.util.concurrent.ConcurrentHashMap

object SourceHealthTracker {

	enum class Status {
		OK,
		UNSTABLE,
		OFFLINE,
	}

	private val failures = ConcurrentHashMap<String, Int>()

	fun recordSuccess(source: MangaSource) {
		failures[source.name] = 0
	}

	fun recordFailure(source: MangaSource) {
		failures.compute(source.name) { _, value -> (value ?: 0) + 1 }
	}

	fun getStatus(source: MangaSourceInfo): Status {
		if (source.mangaSource.isBrokenSource()) return Status.OFFLINE
		return when (failures[source.mangaSource.name] ?: 0) {
			0 -> Status.OK
			in 1..2 -> Status.UNSTABLE
			else -> Status.OFFLINE
		}
	}
}

private fun MangaSource.isBrokenSource(): Boolean = when (this) {
	is MangaParserSource -> isBroken
	is PluginMangaSource -> isBroken
	else -> false
}
