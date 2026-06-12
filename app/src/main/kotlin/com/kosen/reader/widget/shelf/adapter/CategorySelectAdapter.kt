package com.kosen.reader.widget.shelf.adapter

import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.widget.shelf.model.CategoryItem

class CategorySelectAdapter(
	clickListener: OnListItemClickListener<CategoryItem>
) : BaseListAdapter<CategoryItem>() {

	init {
		delegatesManager.addDelegate(categorySelectItemAD(clickListener))
	}
}
