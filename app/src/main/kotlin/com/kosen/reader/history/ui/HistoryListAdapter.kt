package com.kosen.reader.history.ui

import android.content.Context
import com.kosen.reader.core.ui.list.fastscroll.FastScroller
import com.kosen.reader.list.ui.adapter.MangaListAdapter
import com.kosen.reader.list.ui.adapter.MangaListListener
import com.kosen.reader.list.ui.size.ItemSizeResolver

class HistoryListAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
) : MangaListAdapter(listener, sizeResolver), FastScroller.SectionIndexer {

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
