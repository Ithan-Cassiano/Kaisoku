package com.kosen.reader.history.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.kosen.reader.core.db.MangaDatabase
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.IncognitoMode
import javax.inject.Inject

class PrivateHistoryAdminUseCase @Inject constructor(
	@ApplicationContext private val context: Context,
	private val settings: AppSettings,
	private val db: MangaDatabase,
) {

	suspend fun hideAllFromSource(sourceName: String): Int = withContext(Dispatchers.Default) {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			return@withContext 0
		}
		val ids = db.getHistoryDao().findMangaIdsBySource(sourceName)
		ids.forEach { settings.markPrivateHistory(it) }
		ids.size
	}

	suspend fun hideAllWithTag(tagTitle: String): Int = withContext(Dispatchers.Default) {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			return@withContext 0
		}
		val ids = db.getHistoryDao().findMangaIdsByTagTitle("%$tagTitle%")
		ids.forEach { settings.markPrivateHistory(it) }
		ids.size
	}

	suspend fun exportPrivateHistory(uri: Uri): Int = withContext(Dispatchers.IO) {
		val ids = settings.privateHistoryMangaIds
		if (ids.isEmpty()) {
			return@withContext 0
		}
		val array = JSONArray()
		for (id in ids) {
			val history = db.getHistoryDao().find(id) ?: continue
			val manga = db.getMangaDao().find(id)?.manga ?: continue
			array.put(
				JSONObject()
					.put("id", id)
					.put("title", manga.title)
					.put("source", manga.source)
					.put("percent", history.percent),
			)
		}
		context.contentResolver.openOutputStream(uri)?.use { out ->
			out.write(array.toString(2).toByteArray(Charsets.UTF_8))
		}
		array.length()
	}

	suspend fun clearPrivateHistory(): Int = withContext(Dispatchers.Default) {
		val ids = settings.privateHistoryMangaIds.toList()
		settings.clearPrivateHistoryIds()
		ids.size
	}
}
