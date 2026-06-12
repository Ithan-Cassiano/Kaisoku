package com.kosen.reader.core.db

import android.content.Context
import java.io.File

/**
 * Copies the legacy Room database file(s) on first launch after rebrand.
 * Preserves local history, favorites, and settings for users upgrading in place.
 */
internal object LegacyDatabaseMigration {

	private const val NEW_DB_NAME = "kosen-db"
	private const val LEGACY_DB_NAME = "kotatsu-db"

	fun migrateIfNeeded(context: Context) {
		val newDb = context.getDatabasePath(NEW_DB_NAME)
		if (newDb.exists()) {
			return
		}
		val legacyDb = context.getDatabasePath(LEGACY_DB_NAME)
		if (!legacyDb.exists()) {
			return
		}
		copyDatabaseFiles(legacyDb, newDb)
	}

	private fun copyDatabaseFiles(from: File, to: File) {
		for (suffix in DB_FILE_SUFFIXES) {
			val source = dbSibling(from, suffix)
			val target = dbSibling(to, suffix)
			if (source.exists()) {
				source.copyTo(target, overwrite = false)
			}
		}
	}

	private fun dbSibling(base: File, suffix: String): File {
		return File(base.parentFile, base.name + suffix)
	}

	private val DB_FILE_SUFFIXES = listOf("", "-shm", "-wal", "-journal")
}
