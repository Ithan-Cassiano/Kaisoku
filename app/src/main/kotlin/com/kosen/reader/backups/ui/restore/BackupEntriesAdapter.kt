package com.kosen.reader.backups.ui.restore

import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.util.ext.setChecked
import com.kosen.reader.databinding.ItemCheckableMultipleBinding
import com.kosen.reader.list.ui.ListModelDiffCallback.Companion.PAYLOAD_CHECKED_CHANGED
import com.kosen.reader.list.ui.adapter.ListItemType

class BackupSectionsAdapter(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) : BaseListAdapter<BackupSectionModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, backupSectionAD(clickListener))
	}
}

private fun backupSectionAD(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) = adapterDelegateViewBinding<BackupSectionModel, BackupSectionModel, ItemCheckableMultipleBinding>(
	{ layoutInflater, parent -> ItemCheckableMultipleBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		clickListener.onItemClick(item, v)
	}

	bind { payloads ->
		with(binding.root) {
			setText(item.titleResId)
			setChecked(item.isChecked, PAYLOAD_CHECKED_CHANGED in payloads)
			isEnabled = item.isEnabled
		}
	}
}
