package com.kosen.reader.tracker.ui.feed.adapter

import android.content.Context
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.list.fastscroll.FastScroller
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.MangaListListener
import com.kosen.reader.list.ui.adapter.emptyStateListAD
import com.kosen.reader.list.ui.adapter.errorFooterAD
import com.kosen.reader.list.ui.adapter.errorStateListAD
import com.kosen.reader.list.ui.adapter.listHeaderAD
import com.kosen.reader.list.ui.adapter.loadingFooterAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.adapter.quickFilterAD
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.size.ItemSizeResolver
import com.kosen.reader.tracker.ui.feed.model.FeedItem

class FeedAdapter(
	listener: MangaListListener,
	sizeResolver: ItemSizeResolver,
	feedClickListener: OnListItemClickListener<FeedItem>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.FEED, feedItemAD(feedClickListener))
		addDelegate(
			ListItemType.MANGA_NESTED_GROUP,
			updatedMangaAD(
				sizeResolver = sizeResolver,
				listener = listener,
				headerClickListener = listener,
			),
		)
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.FOOTER_ERROR, errorFooterAD(listener))
		addDelegate(ListItemType.STATE_ERROR, errorStateListAD(listener))
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listener))
		addDelegate(ListItemType.QUICK_FILTER, quickFilterAD(listener))
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return findHeader(position)?.getText(context)
	}
}
