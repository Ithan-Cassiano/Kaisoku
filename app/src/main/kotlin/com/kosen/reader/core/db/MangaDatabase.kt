package com.kosen.reader.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.kosen.reader.bookmarks.data.BookmarkEntity
import com.kosen.reader.bookmarks.data.BookmarksDao
import com.kosen.reader.core.db.dao.ChaptersDao
import com.kosen.reader.core.db.dao.MangaDao
import com.kosen.reader.core.db.dao.MangaSourcesDao
import com.kosen.reader.core.db.dao.PreferencesDao
import com.kosen.reader.core.db.dao.TagsDao
import com.kosen.reader.core.db.dao.TrackLogsDao
import com.kosen.reader.core.db.entity.ChapterEntity
import com.kosen.reader.core.db.entity.MangaEntity
import com.kosen.reader.core.db.entity.MangaPrefsEntity
import com.kosen.reader.core.db.entity.MangaSourceEntity
import com.kosen.reader.core.db.entity.MangaTagsEntity
import com.kosen.reader.core.db.entity.TagEntity
import com.kosen.reader.core.db.migrations.Migration10To11
import com.kosen.reader.core.db.migrations.Migration11To12
import com.kosen.reader.core.db.migrations.Migration12To13
import com.kosen.reader.core.db.migrations.Migration13To14
import com.kosen.reader.core.db.migrations.Migration14To15
import com.kosen.reader.core.db.migrations.Migration15To16
import com.kosen.reader.core.db.migrations.Migration16To17
import com.kosen.reader.core.db.migrations.Migration17To18
import com.kosen.reader.core.db.migrations.Migration18To19
import com.kosen.reader.core.db.migrations.Migration19To20
import com.kosen.reader.core.db.migrations.Migration1To2
import com.kosen.reader.core.db.migrations.Migration20To21
import com.kosen.reader.core.db.migrations.Migration21To22
import com.kosen.reader.core.db.migrations.Migration22To23
import com.kosen.reader.core.db.migrations.Migration23To24
import com.kosen.reader.core.db.migrations.Migration24To23
import com.kosen.reader.core.db.migrations.Migration24To25
import com.kosen.reader.core.db.migrations.Migration25To26
import com.kosen.reader.core.db.migrations.Migration26To27
import com.kosen.reader.core.db.migrations.Migration27To28
import com.kosen.reader.core.db.migrations.Migration28To29
import com.kosen.reader.core.db.migrations.Migration2To3
import com.kosen.reader.core.db.migrations.Migration3To4
import com.kosen.reader.core.db.migrations.Migration4To5
import com.kosen.reader.core.db.migrations.Migration5To6
import com.kosen.reader.core.db.migrations.Migration6To7
import com.kosen.reader.core.db.migrations.Migration7To8
import com.kosen.reader.core.db.migrations.Migration8To9
import com.kosen.reader.core.db.migrations.Migration9To10
import com.kosen.reader.core.util.ext.processLifecycleScope
import com.kosen.reader.favourites.data.FavouriteCategoriesDao
import com.kosen.reader.favourites.data.FavouriteCategoryEntity
import com.kosen.reader.favourites.data.FavouriteEntity
import com.kosen.reader.favourites.data.FavouritesDao
import com.kosen.reader.history.data.HistoryDao
import com.kosen.reader.history.data.HistoryEntity
import com.kosen.reader.local.data.index.LocalMangaIndexDao
import com.kosen.reader.local.data.index.LocalMangaIndexEntity
import com.kosen.reader.scrobbling.common.data.ScrobblingDao
import com.kosen.reader.scrobbling.common.data.ScrobblingEntity
import com.kosen.reader.stats.data.StatsDao
import com.kosen.reader.stats.data.StatsEntity
import com.kosen.reader.suggestions.data.SuggestionDao
import com.kosen.reader.suggestions.data.SuggestionEntity
import com.kosen.reader.tracker.data.TrackEntity
import com.kosen.reader.tracker.data.TrackLogEntity
import com.kosen.reader.tracker.data.TracksDao

const val DATABASE_VERSION = 29

@Database(
	entities = [
		MangaEntity::class, TagEntity::class, HistoryEntity::class, MangaTagsEntity::class, ChapterEntity::class,
		FavouriteCategoryEntity::class, FavouriteEntity::class, MangaPrefsEntity::class, TrackEntity::class,
		TrackLogEntity::class, SuggestionEntity::class, BookmarkEntity::class, ScrobblingEntity::class,
		MangaSourceEntity::class, StatsEntity::class, LocalMangaIndexEntity::class,
	],
	version = DATABASE_VERSION,
)
abstract class MangaDatabase : RoomDatabase() {

	abstract fun getHistoryDao(): HistoryDao

	abstract fun getTagsDao(): TagsDao

	abstract fun getMangaDao(): MangaDao

	abstract fun getFavouritesDao(): FavouritesDao

	abstract fun getPreferencesDao(): PreferencesDao

	abstract fun getFavouriteCategoriesDao(): FavouriteCategoriesDao

	abstract fun getTracksDao(): TracksDao

	abstract fun getTrackLogsDao(): TrackLogsDao

	abstract fun getSuggestionDao(): SuggestionDao

	abstract fun getBookmarksDao(): BookmarksDao

	abstract fun getScrobblingDao(): ScrobblingDao

	abstract fun getSourcesDao(): MangaSourcesDao

	abstract fun getStatsDao(): StatsDao

	abstract fun getLocalMangaIndexDao(): LocalMangaIndexDao

	abstract fun getChaptersDao(): ChaptersDao
}

fun getDatabaseMigrations(context: Context): Array<Migration> = arrayOf(
	Migration1To2(),
	Migration2To3(),
	Migration3To4(),
	Migration4To5(),
	Migration5To6(),
	Migration6To7(),
	Migration7To8(),
	Migration8To9(),
	Migration9To10(),
	Migration10To11(),
	Migration11To12(),
	Migration12To13(),
	Migration13To14(),
	Migration14To15(),
	Migration15To16(),
	Migration16To17(context),
	Migration17To18(),
	Migration18To19(),
	Migration19To20(),
	Migration20To21(),
	Migration21To22(),
	Migration22To23(),
	Migration23To24(),
	Migration24To23(),
	Migration24To25(),
	Migration25To26(),
	Migration26To27(),
	Migration27To28(),
	Migration28To29(),
)

fun MangaDatabase(context: Context): MangaDatabase {
	LegacyDatabaseMigration.migrateIfNeeded(context)
	return Room
	.databaseBuilder(context, MangaDatabase::class.java, "kosen-db")
	.addMigrations(*getDatabaseMigrations(context))
	.addCallback(DatabasePrePopulateCallback(context.resources))
	.build()
}

fun InvalidationTracker.removeObserverAsync(observer: InvalidationTracker.Observer) {
	val scope = processLifecycleScope
	if (scope.isActive) {
		processLifecycleScope.launch(Dispatchers.Default, CoroutineStart.ATOMIC) {
			removeObserver(observer)
		}
	}
}
