package com.kosen.reader.search.ui.multi.adapter

import android.annotation.SuppressLint
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.RecycledViewPool
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.core.model.UnknownMangaSource
import com.kosen.reader.core.ui.list.AdapterDelegateClickListenerAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.list.decor.SpacingItemDecoration
import com.kosen.reader.core.util.ext.getDisplayMessage
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.ItemListGroupBinding
import com.kosen.reader.list.ui.MangaSelectionDecoration
import com.kosen.reader.list.ui.adapter.mangaGridItemAD
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.list.ui.size.ItemSizeResolver
import com.kosen.reader.search.ui.multi.SearchResultsListModel

@SuppressLint("NotifyDataSetChanged")
fun searchResultsAD(
	sharedPool: RecycledViewPool,
	sizeResolver: ItemSizeResolver,
	selectionDecoration: MangaSelectionDecoration,
	listener: OnListItemClickListener<MangaListModel>,
	itemClickListener: OnListItemClickListener<SearchResultsListModel>,
) = adapterDelegateViewBinding<SearchResultsListModel, ListModel, ItemListGroupBinding>(
	{ layoutInflater, parent -> ItemListGroupBinding.inflate(layoutInflater, parent, false) },
) {

	binding.recyclerView.setRecycledViewPool(sharedPool)
	val adapter = ListDelegationAdapter(mangaGridItemAD(sizeResolver, listener))
	binding.recyclerView.addItemDecoration(selectionDecoration)
	binding.recyclerView.adapter = adapter
	val spacing = context.resources.getDimensionPixelOffset(R.dimen.grid_spacing_outer)
	binding.recyclerView.addItemDecoration(SpacingItemDecoration(spacing, withBottomPadding = true))
	val eventListener = AdapterDelegateClickListenerAdapter(this, itemClickListener)
	binding.buttonMore.setOnClickListener(eventListener)

	bind {
		binding.textViewTitle.text = item.getTitle(context)
		binding.buttonMore.isVisible = item.source !== UnknownMangaSource
		adapter.items = item.list
		adapter.notifyDataSetChanged()
		binding.recyclerView.isGone = item.list.isEmpty()
		binding.textViewError.textAndVisible = item.error?.getDisplayMessage(context.resources)
	}
}
