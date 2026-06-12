package com.kosen.reader.suggestions.domain

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.kosen.reader.core.db.MangaDatabase
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.db.entity.toEntities
import com.kosen.reader.core.db.entity.toEntity
import com.kosen.reader.core.db.entity.toManga
import com.kosen.reader.core.db.entity.toMangaTags
import com.kosen.reader.core.db.entity.toMangaTagsList
import com.kosen.reader.core.model.toMangaSources
import com.kosen.reader.core.util.ext.mapItems
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.filterByNsfwOptions
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.suggestions.data.SuggestionEntity
import com.kosen.reader.suggestions.domain.withSuggestionContentTypeTag
import com.kosen.reader.suggestions.data.SuggestionWithManga
import javax.inject.Inject

class SuggestionRepository @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
) {

	fun observeAll(): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll().mapItems {
			it.toManga()
		}
	}

	fun observeAll(limit: Int, filterOptions: Set<ListFilterOption>): Flow<List<Manga>> {
		return db.getSuggestionDao().observeAll(limit, filterOptions).mapItems {
			it.toManga()
		}.map { list -> list.filterByNsfwOptions(filterOptions, settings) }
	}

	suspend fun getRandomList(limit: Int): List<Manga> {
		return db.getSuggestionDao().getRandom(limit).map {
			it.toManga()
		}
	}

	suspend fun getRandomList(limit: Int, filterOptions: Set<ListFilterOption>): List<Manga> {
		return db.getSuggestionDao().getRandom(limit, filterOptions).map {
			it.toManga()
		}.filterByNsfwOptions(filterOptions, settings)
	}

	suspend fun clear() {
		db.getSuggestionDao().deleteAll()
	}

	suspend fun isEmpty(): Boolean {
		return db.getSuggestionDao().count() == 0
	}

	suspend fun getTopTags(limit: Int): List<MangaTag> {
		return db.getSuggestionDao().getTopTags(limit)
			.toMangaTagsList()
	}

	suspend fun getTopSources(limit: Int): List<MangaSource> {
		return db.getSuggestionDao().getTopSources(limit)
			.toMangaSources()
	}

	suspend fun replace(suggestions: Iterable<MangaSuggestion>) {
		db.withTransaction {
			db.getSuggestionDao().deleteAll()
			suggestions.forEach { (manga, relevance) ->
				val storedManga = manga.withSuggestionContentTypeTag()
				val tags = storedManga.tags.toEntities()
				db.getTagsDao().upsert(tags)
				db.getMangaDao().upsert(storedManga.toEntity(), tags)
				db.getSuggestionDao().upsert(
					SuggestionEntity(
						mangaId = storedManga.id,
						relevance = relevance,
						createdAt = System.currentTimeMillis(),
					),
				)
			}
		}
	}

	private fun SuggestionWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
