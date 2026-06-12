package com.kosen.reader.reader.ui

import com.kosen.reader.bookmarks.domain.Bookmark
import com.kosen.reader.parsers.model.MangaChapter
import com.kosen.reader.reader.ui.pager.ReaderPage

interface ReaderNavigationCallback {

	fun onPageSelected(page: ReaderPage): Boolean

	fun onChapterSelected(chapter: MangaChapter): Boolean

	fun onBookmarkSelected(bookmark: Bookmark): Boolean = onPageSelected(
		ReaderPage(bookmark.toMangaPage(), bookmark.page, bookmark.chapterId),
	)
}
