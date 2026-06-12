package com.kosen.reader.explore.ui

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.adapter.ListItemType

class ExploreGridSpanSizeLookup(
	private val adapter: BaseListAdapter<ListModel>,
	private val layoutManager: GridLayoutManager,
) : SpanSizeLookup() {

	override fun getSpanSize(position: Int): Int {
		val itemType = adapter.getItemViewType(position)
		return if (itemType == ListItemType.EXPLORE_SOURCE_GRID.ordinal) 1 else layoutManager.spanCount
	}
}
