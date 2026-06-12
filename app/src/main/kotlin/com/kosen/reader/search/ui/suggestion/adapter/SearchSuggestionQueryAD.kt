package com.kosen.reader.search.ui.suggestion.adapter

import android.view.View
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.databinding.ItemSearchSuggestionQueryBinding
import com.kosen.reader.search.domain.SearchKind
import com.kosen.reader.search.ui.suggestion.SearchSuggestionListener
import com.kosen.reader.search.ui.suggestion.model.SearchSuggestionItem

fun searchSuggestionQueryAD(
	listener: SearchSuggestionListener,
) =
	adapterDelegateViewBinding<SearchSuggestionItem.RecentQuery, SearchSuggestionItem, ItemSearchSuggestionQueryBinding>(
		{ inflater, parent -> ItemSearchSuggestionQueryBinding.inflate(inflater, parent, false) },
	) {

		val viewClickListener = View.OnClickListener { v ->
			listener.onQueryClick(item.query, SearchKind.SIMPLE, v.id != R.id.button_complete)
		}

		binding.root.setOnClickListener(viewClickListener)
		binding.buttonComplete.setOnClickListener(viewClickListener)
		binding.buttonRemove.setOnClickListener {
			listener.onRemoveQuery(item.query)
		}

		bind {
			binding.textViewTitle.text = item.query
		}
	}
