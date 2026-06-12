package com.kosen.reader.reader.ui.pager.doublepage

import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.reader.ui.pager.ReaderPage
import com.kosen.reader.reader.ui.pager.standard.PageHolder

fun RecyclerView.visiblePageHolders(): Sequence<PageHolder> {
	val lm = layoutManager as? LinearLayoutManager ?: return emptySequence()
	return (lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()).asSequence()
		.mapNotNull { findViewHolderForAdapterPosition(it) as? PageHolder }
}

fun RecyclerView.allPageHolders(): Sequence<PageHolder> {
	return children.mapNotNull {
		findContainingViewHolder(it) as? PageHolder
	}
}

fun List<ReaderPage>.padForDoublePage(coverPage: Boolean): List<ReaderPage> {
	if (isEmpty()) return this
	val result = ArrayList<ReaderPage>(size + size / 20 + 1)
	var currentChapterId = first().chapterId

	if (coverPage) {
		val first = first()
		result.add(
			ReaderPage(
				id = Long.MIN_VALUE,
				url = "",
				preview = null,
				chapterId = first.chapterId,
				index = -1,
				source = first.source,
			),
		)
	}

	for (page in this) {
		if (page.chapterId != currentChapterId) {
			if (result.size % 2 != 0) {
				val last = result.last()
				result.addSpacer(last.chapterId, last.source)
			}
			currentChapterId = page.chapterId
			if (coverPage) {
				result.addSpacer(page.chapterId, page.source)
			}
		}
		result.add(page)
	}
	return result
}

private fun MutableList<ReaderPage>.addSpacer(chapterId: Long, source: MangaSource) {
	add(
		ReaderPage(
			id = Long.MIN_VALUE + size,
			url = "",
			preview = null,
			chapterId = chapterId,
			index = -1,
			source = source,
		),
	)
}
