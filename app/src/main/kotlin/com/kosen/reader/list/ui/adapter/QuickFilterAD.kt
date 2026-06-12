package com.kosen.reader.list.ui.adapter

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.databinding.ItemQuickFilterBinding
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.QuickFilter
import java.lang.ref.WeakReference

fun quickFilterAD(
	listener: QuickFilterClickListener,
) = adapterDelegateViewBinding<QuickFilter, ListModel, ItemQuickFilterBinding>(
	{ layoutInflater, parent -> ItemQuickFilterBinding.inflate(layoutInflater, parent, false) }
) {

	binding.chipsTags.onChipClickListener = WeakQuickFilterChipClickListener(listener)

	bind {
		binding.chipsTags.setChips(item.items)
	}
}

private class WeakQuickFilterChipClickListener(
	listener: QuickFilterClickListener,
) : ChipsView.OnChipClickListener {

	private val listenerRef = WeakReference(listener)

	override fun onChipClick(chip: com.google.android.material.chip.Chip, data: Any?) {
		if (data is ListFilterOption) {
			listenerRef.get()?.onFilterOptionClick(data)
		}
	}
}
