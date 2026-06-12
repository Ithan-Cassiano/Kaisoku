package com.kosen.reader.bookmarks.domain

import com.kosen.reader.core.util.MimeTypes
import com.kosen.reader.core.util.ext.isImage
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaPage
import java.time.Instant

data class Bookmark(
	val manga: Manga,
	val pageId: Long,
	val chapterId: Long,
	val page: Int,
	val scroll: Int,
	val imageUrl: String,
	val createdAt: Instant,
	val percent: Float,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is Bookmark &&
			manga.id == other.manga.id &&
			chapterId == other.chapterId &&
			page == other.page
	}

	fun toMangaPage() = MangaPage(
		id = pageId,
		url = imageUrl,
		preview = imageUrl.takeIf {
			MimeTypes.getMimeTypeFromUrl(it)?.isImage == true
		},
		source = manga.source,
	)
}
