package com.kosen.reader.history.data

import androidx.room.withTransaction
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.kosen.reader.core.db.MangaDatabase
import com.kosen.reader.core.db.entity.toEntity
import com.kosen.reader.core.db.entity.toManga
import com.kosen.reader.core.db.entity.toMangaList
import com.kosen.reader.core.db.entity.toMangaTags
import com.kosen.reader.core.db.entity.toMangaTagsList
import com.kosen.reader.core.model.MangaHistory
import com.kosen.reader.core.model.isLocal
import com.kosen.reader.core.model.isNsfw
import com.kosen.reader.core.model.toMangaSources
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.core.prefs.ProgressIndicatorMode
import com.kosen.reader.core.ui.util.ReversibleHandle
import com.kosen.reader.core.util.ext.mapItems
import com.kosen.reader.history.domain.model.MangaWithHistory
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.ListSortOrder
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.parsers.util.findById
import com.kosen.reader.parsers.util.levenshteinDistance
import com.kosen.reader.scrobbling.common.domain.Scrobbler
import com.kosen.reader.scrobbling.common.domain.tryScrobble
import com.kosen.reader.search.domain.SearchKind
import com.kosen.reader.tracker.domain.CheckNewChaptersUseCase
import javax.inject.Inject
import javax.inject.Provider

@Reusable
class HistoryRepository @Inject constructor(
	private val db: MangaDatabase,
	private val settings: AppSettings,
	private val scrobblers: Set<@JvmSuppressWildcards Scrobbler>,
	private val mangaRepository: MangaDataRepository,
	private val localObserver: HistoryLocalObserver,
	private val newChaptersUseCaseProvider: Provider<CheckNewChaptersUseCase>,
) {

	suspend fun getList(offset: Int, limit: Int): List<Manga> {
		val entities = db.getHistoryDao().findAll(offset, limit)
		return entities.map { it.toManga() }
	}

	suspend fun search(query: String, kind: SearchKind, limit: Int): List<Manga> {
		val dao = db.getHistoryDao()
		val q = "%$query%"
		val entities = when (kind) {
			SearchKind.SIMPLE,
			SearchKind.TITLE -> dao.searchByTitle(q, limit).sortedBy { it.manga.title.levenshteinDistance(query) }

			SearchKind.AUTHOR -> dao.searchByAuthor(q, limit)
			SearchKind.TAG -> dao.searchByTag(q, limit)
		}
		return entities.toMangaList()
	}

	suspend fun getLastOrNull(): Manga? {
		val entities = db.getHistoryDao().findAll(0, 100)
		for (entity in entities) {
			val manga = entity.toManga()
			if (isVisibleInMainHistory(manga)) {
				return manga
			}
		}
		return null
	}

	suspend fun getContinueReadingMangaOrNull(): Manga? {
		val entities = db.getHistoryDao().findAll(0, 64)
		for (entity in entities) {
			val percent = entity.history.percent
			if (percent !in 0.01f..0.99f) {
				continue
			}
			val manga = entity.toManga()
			if (isVisibleInMainHistory(manga)) {
				return manga
			}
		}
		return null
	}

	fun observeLast(): Flow<Manga?> {
		return db.getHistoryDao().observeAll(100).map { list ->
			list.firstOrNull { isVisibleInMainHistory(it.toManga()) }?.toManga()
		}
	}

	fun observeAll(): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll().mapItems {
			it.toManga()
		}
	}

	fun observeAll(limit: Int): Flow<List<Manga>> {
		return db.getHistoryDao().observeAll(limit).mapItems {
			it.toManga()
		}
	}

	fun observeAllWithHistory(
		order: ListSortOrder,
		filterOptions: Set<ListFilterOption>,
		limit: Int
	): Flow<List<MangaWithHistory>> {
		if (ListFilterOption.Downloaded in filterOptions) {
			return localObserver.observeAll(order, filterOptions.withoutPrivateHistoryFilter(), limit)
				.map { list -> filterPrivateHistory(list, filterOptions) }
		}
		return db.getHistoryDao().observeAll(order, filterOptions.withoutPrivateHistoryFilter(), limit).mapItems {
			MangaWithHistory(
				it.toManga(),
				it.history.toMangaHistory(),
			)
		}.map { list -> filterPrivateHistory(list, filterOptions) }
	}

	private fun Set<ListFilterOption>.withoutPrivateHistoryFilter(): Set<ListFilterOption> =
		if (ListFilterOption.Macro.PRIVATE_HISTORY in this) {
			this - ListFilterOption.Macro.PRIVATE_HISTORY
		} else {
			this
		}

	private fun filterPrivateHistory(
		list: List<MangaWithHistory>,
		filterOptions: Set<ListFilterOption>,
	): List<MangaWithHistory> {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			val privateIds = settings.privateHistoryMangaIds
			if (privateIds.isEmpty()) {
				return list
			}
			val showPrivateOnly = ListFilterOption.Macro.PRIVATE_HISTORY in filterOptions
			return list.filter { item ->
				val isPrivate = item.manga.id in privateIds
				if (showPrivateOnly) isPrivate else !isPrivate
			}
		}
		val showPrivateOnly = ListFilterOption.Macro.PRIVATE_HISTORY in filterOptions
		return list.filter { item ->
			val isHidden = isHiddenHistoryEntry(item.manga)
			if (showPrivateOnly) isHidden else !isHidden
		}
	}

	private fun isHiddenHistoryEntry(manga: Manga): Boolean =
		when (settings.incognitoMode) {
			IncognitoMode.HIDDEN_HISTORY -> manga.isNsfw() || settings.isPrivateHistory(manga.id)
			else -> settings.isPrivateHistory(manga.id)
		}

	private fun isVisibleInMainHistory(manga: Manga): Boolean = !isHiddenHistoryEntry(manga)

	fun canHideFromMainHistory(manga: Manga): Boolean =
		settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY && !isHiddenHistoryEntry(manga)

	fun canShowInMainHistory(manga: Manga): Boolean =
		settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY &&
			settings.isPrivateHistory(manga.id) &&
			!manga.isNsfw()

	fun isHiddenFromMainHistory(manga: Manga): Boolean = isHiddenHistoryEntry(manga)

	suspend fun hideFromMainHistory(mangaId: Long) {
		if (settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY) {
			settings.markPrivateHistory(mangaId)
		}
	}

	suspend fun showInMainHistory(mangaId: Long) {
		settings.unmarkPrivateHistory(mangaId)
	}

	fun observeOne(id: Long): Flow<MangaHistory?> {
		return db.getHistoryDao().observe(id).map {
			it?.toMangaHistory()
		}
	}

	suspend fun addOrUpdate(manga: Manga, chapterId: Long, page: Int, scroll: Int, percent: Float, force: Boolean) {
		if (!force && shouldSkip(manga)) {
			return
		}
		assert(manga.chapters != null)
		db.withTransaction {
			mangaRepository.storeManga(manga, replaceExisting = true)
			val branch = manga.chapters?.findById(chapterId)?.branch
			db.getHistoryDao().upsert(
				HistoryEntity(
					mangaId = manga.id,
					createdAt = System.currentTimeMillis(),
					updatedAt = System.currentTimeMillis(),
					chapterId = chapterId,
					page = page,
					scroll = scroll.toFloat(), // we migrate to int, but decide to not update database
					percent = percent,
					chaptersCount = manga.chapters?.count { it.branch == branch } ?: 0,
					deletedAt = 0L,
				),
			)
			if (settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY && manga.isNsfw()) {
				settings.markPrivateHistory(manga.id)
			}
			newChaptersUseCaseProvider.get()(manga, chapterId)
			scrobblers.forEach { it.tryScrobble(manga, chapterId) }
		}
	}

	suspend fun getOne(manga: Manga): MangaHistory? {
		return db.getHistoryDao().find(manga.id)?.recoverIfNeeded(manga)?.toMangaHistory()
	}

	suspend fun getProgress(mangaId: Long, mode: ProgressIndicatorMode): ReadingProgress? {
		val entity = db.getHistoryDao().find(mangaId) ?: return null
		val fixedPercent = if (ReadingProgress.isCompleted(entity.percent)) 1f else entity.percent
		return ReadingProgress(
			percent = fixedPercent,
			totalChapters = entity.chaptersCount,
			mode = mode,
		).takeIf { it.isValid() }
	}

	suspend fun getProgressMap(mangaIds: Collection<Long>, mode: ProgressIndicatorMode): Map<Long, ReadingProgress> {
		if (mangaIds.isEmpty()) {
			return emptyMap()
		}
		val entities = db.getHistoryDao().findAllByIds(mangaIds.toLongArray())
		val result = HashMap<Long, ReadingProgress>(entities.size)
		for (entity in entities) {
			val fixedPercent = if (ReadingProgress.isCompleted(entity.percent)) 1f else entity.percent
			val progress = ReadingProgress(
				percent = fixedPercent,
				totalChapters = entity.chaptersCount,
				mode = mode,
			).takeIf { it.isValid() } ?: continue
			result[entity.mangaId] = progress
		}
		return result
	}

	suspend fun clear() {
		db.getHistoryDao().clear()
	}

	suspend fun delete(manga: Manga) = db.withTransaction {
		db.getHistoryDao().delete(manga.id)
		mangaRepository.gcChaptersCache()
	}

	suspend fun deleteAfter(minDate: Long) = db.withTransaction {
		db.getHistoryDao().deleteAfter(minDate)
		mangaRepository.gcChaptersCache()
	}

	suspend fun deleteNotFavorite() = db.withTransaction {
		db.getHistoryDao().deleteNotFavorite()
		mangaRepository.gcChaptersCache()
	}

	suspend fun delete(ids: Collection<Long>): ReversibleHandle {
		db.withTransaction {
			for (id in ids) {
				db.getHistoryDao().delete(id)
			}
			mangaRepository.gcChaptersCache()
		}
		return ReversibleHandle {
			recover(ids)
		}
	}

	/**
	 * Try to replace one manga with another one
	 * Useful for replacing saved manga on deleting it with remote source
	 */
	suspend fun deleteOrSwap(manga: Manga, alternative: Manga?) {
		if (alternative == null || db.getMangaDao().update(alternative.toEntity()) <= 0) {
			delete(manga)
		}
	}

	suspend fun getPopularTags(limit: Int): List<MangaTag> {
		return db.getHistoryDao().findPopularTags(limit).toMangaTagsList()
	}

	suspend fun getPopularSources(limit: Int): List<MangaSource> {
		return db.getHistoryDao().findPopularSources(limit).toMangaSources()
	}

	fun shouldSkip(manga: Manga): Boolean = settings.shouldSkipHistory(manga)

	fun observeShouldSkip(manga: Manga): Flow<Boolean> {
		return settings.observe(AppSettings.KEY_INCOGNITO_MODE, AppSettings.KEY_INCOGNITO_NSFW)
			.map { shouldSkip(manga) }
			.distinctUntilChanged()
	}

	suspend fun reconcilePrivateHistory() {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			return
		}
		var offset = 0
		val pageSize = 100
		while (true) {
			val batch = db.getHistoryDao().findAll(offset, pageSize)
			if (batch.isEmpty()) {
				break
			}
			for (entity in batch) {
				val manga = entity.toManga()
				if (manga.isNsfw()) {
					settings.markPrivateHistory(manga.id)
				}
			}
			if (batch.size < pageSize) {
				break
			}
			offset += pageSize
		}
	}

	private suspend fun recover(ids: Collection<Long>) {
		db.withTransaction {
			for (id in ids) {
				db.getHistoryDao().recover(id)
			}
		}
	}

	private suspend fun HistoryEntity.recoverIfNeeded(manga: Manga): HistoryEntity {
		val chapters = manga.chapters
		if (manga.isLocal || chapters.isNullOrEmpty() || chapters.findById(chapterId) != null) {
			return this
		}
		val newChapterId = chapters.getOrNull(
			(chapters.size * percent).toInt(),
		)?.id ?: return this
		val newEntity = copy(chapterId = newChapterId)
		db.getHistoryDao().update(newEntity)
		return newEntity
	}

	private fun HistoryWithManga.toManga() = manga.toManga(tags.toMangaTags(), null)
}
