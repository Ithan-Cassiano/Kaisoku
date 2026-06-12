package com.kosen.reader.explore.ui.adapter

import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.explore.ui.model.MangaSourceItem
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.emptyHintAD
import com.kosen.reader.list.ui.adapter.listHeaderAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.Manga

open class ExploreAdapter(
	listener: ExploreListEventListener,
	clickListener: OnListItemClickListener<MangaSourceItem>,
	mangaClickListener: OnListItemClickListener<Manga>,
) : BaseListAdapter<ListModel>() {

	var progressMap: Map<Long, ReadingProgress?> = emptyMap()

	init {
		addDelegate(ListItemType.EXPLORE_BUTTONS, exploreButtonsAD(listener))
		addDelegate(
			ListItemType.EXPLORE_SUGGESTION,
			exploreRecommendationItemAD(mangaClickListener) { progressMap },
		)
		addDelegate(ListItemType.HEADER, listHeaderAD(listener))
		addDelegate(ListItemType.EXPLORE_SOURCE_LIST, exploreSourceListItemAD(clickListener))
		addDelegate(ListItemType.EXPLORE_SOURCE_GRID, exploreSourceGridItemAD(clickListener))
		addDelegate(ListItemType.HINT_EMPTY, emptyHintAD(listener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.EXPLORE_SUGGESTIONS_EMPTY, suggestionsEmptyAD(listener))
	}
}
