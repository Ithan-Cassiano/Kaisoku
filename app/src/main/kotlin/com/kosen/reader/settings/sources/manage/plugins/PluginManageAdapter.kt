package com.kosen.reader.settings.sources.manage.plugins

import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.databinding.ItemEmptyHintBinding
import com.kosen.reader.databinding.ItemSourceConfigBinding
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.model.ListModel

class PluginManageAdapter(
	onDeleteClick: (PluginManageItem.Plugin) -> Unit,
	onUpdateClick: (PluginManageItem.Plugin) -> Unit,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.CHAPTER_LIST, pluginItemDelegate(onDeleteClick, onUpdateClick))
		addDelegate(ListItemType.HINT_EMPTY, pluginPlaceholderDelegate())
	}
}

private fun pluginItemDelegate(
	onDeleteClick: (PluginManageItem.Plugin) -> Unit,
	onUpdateClick: (PluginManageItem.Plugin) -> Unit,
) = adapterDelegateViewBinding<PluginManageItem.Plugin, ListModel, ItemSourceConfigBinding>(
	{ layoutInflater, parent -> ItemSourceConfigBinding.inflate(layoutInflater, parent, false) },
) {

	binding.imageViewIcon.setImageResource(R.drawable.ic_services)
	binding.imageViewIcon.background = null
	binding.imageViewMenu.isGone = true
	binding.imageViewRemove.isVisible = true
	binding.imageViewRemove.setImageResource(R.drawable.ic_delete)
	binding.imageViewRemove.contentDescription = context.getString(R.string.delete)
	binding.imageViewAdd.setImageResource(R.drawable.ic_download)
	binding.imageViewAdd.contentDescription = context.getString(R.string.update)

	bind {
		binding.textViewTitle.text = item.displayName
		val parts = ArrayList<String>(3)
		item.repository?.takeIf { it.isNotBlank() }?.let(parts::add)
		item.installedTag?.takeIf { it.isNotBlank() }?.let(parts::add)
		binding.textViewDescription.text = if (parts.isEmpty()) item.jarName else parts.joinToString(" - ")
		binding.imageViewRemove.setOnClickListener { onDeleteClick(item) }
		binding.imageViewAdd.isVisible = item.hasUpdate
		binding.imageViewAdd.setOnClickListener(
			if (item.hasUpdate) View.OnClickListener { onUpdateClick(item) } else null,
		)
	}
}

private fun pluginPlaceholderDelegate() =
	adapterDelegateViewBinding<PluginManageItem.Placeholder, ListModel, ItemEmptyHintBinding>(
		{ layoutInflater, parent -> ItemEmptyHintBinding.inflate(layoutInflater, parent, false) },
	) {
		binding.icon.setImageResource(R.drawable.ic_empty_feed)

		bind {
			binding.textPrimary.setText(item.titleResId)
			binding.textSecondary.setTextAndVisible(item.summaryResId ?: 0)
		}
	}
