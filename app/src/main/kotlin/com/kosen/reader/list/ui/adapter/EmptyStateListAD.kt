package com.kosen.reader.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.databinding.ItemEmptyStateBinding
import com.kosen.reader.list.ui.model.EmptyState
import com.kosen.reader.list.ui.model.ListModel

fun emptyStateListAD(
	listener: ListStateHolderListener?,
) = adapterDelegateViewBinding<EmptyState, ListModel, ItemEmptyStateBinding>(
	{ inflater, parent -> ItemEmptyStateBinding.inflate(inflater, parent, false) },
) {

	if (listener != null) {
		binding.buttonRetry?.setOnClickListener { listener.onEmptyActionClick() }
		binding.buttonSecondary?.setOnClickListener { listener.onEmptySecondaryActionClick() }
		binding.buttonTertiary?.setOnClickListener { listener.onEmptyTertiaryActionClick() }
	}

	bind {
		if (item.icon == 0) {
			binding.icon.isVisible = false
			binding.icon.disposeImage()
		} else {
			binding.icon.isVisible = true
			binding.icon.setImageAsync(item.icon)
		}
		binding.textPrimary.setText(item.textPrimary)
		binding.textSecondary.setTextAndVisible(item.textSecondary)
		if (listener != null) {
			binding.buttonRetry?.setTextAndVisible(item.actionStringRes)
			binding.buttonSecondary?.setTextAndVisible(item.secondaryActionStringRes)
			binding.buttonTertiary?.setTextAndVisible(item.tertiaryActionStringRes)
		}
	}
}
