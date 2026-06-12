package com.kosen.reader.bookmarks.ui.adapter

import android.content.Context
import com.kosen.reader.bookmarks.domain.Bookmark
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.list.fastscroll.FastScroller
import com.kosen.reader.list.ui.adapter.ListHeaderClickListener
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.emptyStateListAD
import com.kosen.reader.list.ui.adapter.errorStateListAD
import com.kosen.reader.list.ui.adapter.listHeaderAD
import com.kosen.reader.list.ui.adapter.loadingFooterAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel

class BookmarksAdapter(
	clickListener: OnListItemClickListener<Bookmark>,
	headerClickListener: ListHeaderClickListener?,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.PAGE_THUMB, bookmarkLargeAD(clickListener))
		addDelegate(ListItemType.HEADER, listHeaderAD(headerClickListener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(null))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
