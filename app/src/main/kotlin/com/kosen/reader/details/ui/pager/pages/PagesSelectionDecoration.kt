package com.kosen.reader.details.ui.pager.pages

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.kosen.reader.core.util.ext.getItem
import com.kosen.reader.list.ui.MangaSelectionDecoration

class PagesSelectionDecoration(context: Context) : MangaSelectionDecoration(context) {

	override fun getItemId(parent: RecyclerView, child: View): Long {
		val holder = parent.getChildViewHolder(child) ?: return RecyclerView.NO_ID
		val item = holder.getItem(PageThumbnail::class.java) ?: return RecyclerView.NO_ID
		return item.page.id
	}
}
