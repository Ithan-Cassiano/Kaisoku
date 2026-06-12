package com.kosen.reader.list.ui.adapter

import android.view.View
import com.kosen.reader.list.ui.model.ListHeader

interface ListHeaderClickListener {

	fun onListHeaderClick(item: ListHeader, view: View)

	fun onListHeaderFilterClick(item: ListHeader, view: View) = Unit
}
