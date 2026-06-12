package com.kosen.reader.favourites.ui.categories.adapter

import com.kosen.reader.core.ui.ReorderableListAdapter
import com.kosen.reader.favourites.ui.categories.FavouriteCategoriesListListener
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.ListStateHolderListener
import com.kosen.reader.list.ui.adapter.emptyStateListAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel

class CategoriesAdapter(
	onItemClickListener: FavouriteCategoriesListListener,
	listListener: ListStateHolderListener,
) : ReorderableListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CATEGORY_LARGE, categoryAD(onItemClickListener))
		addDelegate(ListItemType.NAV_ITEM, allCategoriesAD(onItemClickListener))
		addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(listListener))
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}
}
