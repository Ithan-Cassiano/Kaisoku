package com.kosen.reader.list.ui.adapter

import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.badge.BadgeDrawable
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.databinding.ItemHeaderBinding
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.ListModel

fun listHeaderAD(
	listener: ListHeaderClickListener?,
) = adapterDelegateViewBinding<ListHeader, ListModel, ItemHeaderBinding>(
	{ inflater, parent -> ItemHeaderBinding.inflate(inflater, parent, false) },
) {
	var badge: BadgeDrawable? = null

	if (listener != null) {
		binding.buttonMore.setOnClickListener {
			listener.onListHeaderClick(item, it)
		}
		binding.buttonFilter.setOnClickListener {
			listener.onListHeaderFilterClick(item, it)
		}
	}

	bind {
		binding.textViewTitle.text = item.getText(context)
		if (item.filterButtonTextRes == 0) {
			binding.buttonFilter.isGone = true
			binding.buttonFilter.text = null
		} else {
			binding.buttonFilter.setText(item.filterButtonTextRes)
			binding.buttonFilter.isVisible = true
		}
		if (item.buttonTextRes == 0) {
			binding.buttonMore.isInvisible = true
			binding.buttonMore.text = null
			binding.buttonMore.clearBadge(badge)
		} else {
			binding.buttonMore.setText(item.buttonTextRes)
			binding.buttonMore.isVisible = true
			badge = itemView.bindBadge(badge, item.badge)
		}
	}
}
