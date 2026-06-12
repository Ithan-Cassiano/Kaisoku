package com.kosen.reader.settings.sources.repo

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.core.parser.mihon.repo.MihonRepoExtensionDescriptor
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.list.fastscroll.FastScroller
import com.kosen.reader.core.util.ext.drawableStart
import com.kosen.reader.core.util.ext.getThemeDimensionPixelOffset
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.ItemEmptyHintBinding
import com.kosen.reader.databinding.ItemSourceCatalogBinding
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel
import java.util.Locale
import androidx.appcompat.R as appcompatR

sealed interface MihonRepoExtensionListItem : ListModel {

	data class Extension(
		val descriptor: MihonRepoExtensionDescriptor,
	) : MihonRepoExtensionListItem {

		override fun areItemsTheSame(other: ListModel): Boolean {
			return other is Extension && other.descriptor.extension.pkgName == descriptor.extension.pkgName
		}
	}

	data class Hint(
		@DrawableRes val icon: Int,
		@StringRes val title: Int,
		@StringRes val text: Int,
	) : MihonRepoExtensionListItem {

		override fun areItemsTheSame(other: ListModel): Boolean {
			return other is Hint && other.title == title
		}
	}
}

class MihonRepoExtensionsAdapter(
	listener: OnListItemClickListener<MihonRepoExtensionListItem.Extension>,
) : BaseListAdapter<ListModel>(), FastScroller.SectionIndexer {

	init {
		addDelegate(ListItemType.INFO, mihonRepoExtensionAD(listener))
		addDelegate(ListItemType.HINT_EMPTY, mihonRepoExtensionHintAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items.getOrNull(position) as? MihonRepoExtensionListItem.Extension)
			?.descriptor
			?.extension
			?.name
			?.take(1)
	}
}

private fun mihonRepoExtensionAD(
	listener: OnListItemClickListener<MihonRepoExtensionListItem.Extension>,
) = adapterDelegateViewBinding<MihonRepoExtensionListItem.Extension, ListModel, ItemSourceCatalogBinding>(
	{ layoutInflater, parent ->
		ItemSourceCatalogBinding.inflate(layoutInflater, parent, false)
	},
) {

	binding.imageViewAdd.setOnClickListener { v ->
		if (item.descriptor.toAction().isEnabled) {
			listener.onItemClick(item, v)
		}
	}
	binding.root.setOnClickListener { v ->
		if (item.descriptor.toAction().isEnabled) {
			listener.onItemClick(item, v)
		}
	}
	val basePadding = context.getThemeDimensionPixelOffset(
		appcompatR.attr.listPreferredItemPaddingEnd,
		binding.root.paddingStart,
	)
	binding.root.updatePaddingRelative(
		end = (basePadding - context.resources.getDimensionPixelOffset(R.dimen.margin_small)).coerceAtLeast(0),
	)

	bind {
		val descriptor = item.descriptor
		val action = descriptor.toAction()
		binding.textViewTitle.text = descriptor.extension.name
		binding.textViewDescription.textAndVisible = buildRepoExtensionSummary(context, descriptor)
		binding.textViewDescription.drawableStart = when {
			descriptor.hasUpdate -> ContextCompat.getDrawable(context, R.drawable.ic_updated)
			descriptor.isInstalledPrivately -> ContextCompat.getDrawable(context, R.drawable.ic_check)
			descriptor.isInstalledExternally -> ContextCompat.getDrawable(context, R.drawable.ic_open_external)
			else -> null
		}
		binding.imageViewIcon.apply {
			val iconUrl = descriptor.extension.iconUrl
			if (iconUrl.isBlank()) {
				setImageAsync(R.drawable.ic_sync)
			} else {
				setImageAsync(iconUrl)
			}
		}
		binding.imageViewAdd.setImageResource(action.icon)
		binding.imageViewAdd.isEnabled = action.isEnabled
		binding.imageViewAdd.alpha = if (action.isEnabled) 1f else 0.6f
		binding.imageViewAdd.contentDescription = context.getString(action.label)
		binding.imageViewAdd.tooltipText = binding.imageViewAdd.contentDescription
	}
}

private fun mihonRepoExtensionHintAD() =
	adapterDelegateViewBinding<MihonRepoExtensionListItem.Hint, ListModel, ItemEmptyHintBinding>(
		{ inflater, parent -> ItemEmptyHintBinding.inflate(inflater, parent, false) },
	) {

		binding.buttonRetry.isVisible = false

		bind {
			binding.icon.setImageAsync(item.icon)
			binding.textPrimary.setText(item.title)
			binding.textSecondary.setTextAndVisible(item.text)
		}
	}

private fun buildRepoExtensionSummary(
	context: Context,
	descriptor: MihonRepoExtensionDescriptor,
): String = buildList {
	descriptor.extension.lang
			.takeIf { it.isNotBlank() }
			?.let { add(it.uppercase(Locale.ROOT)) }
	add(descriptor.extension.versionName)
	when {
		descriptor.hasUpdate -> add(context.getString(R.string.extension_update_available))
		descriptor.isInstalledPrivately -> add(context.getString(R.string.extension_installed_privately))
		descriptor.isInstalledExternally -> add(context.getString(R.string.extension_installed_externally))
	}
}.joinToString(" • ")

private fun MihonRepoExtensionDescriptor.toAction(): RepoExtensionAction {
	return when {
		hasUpdate -> RepoExtensionAction(
			icon = R.drawable.ic_updated,
			label = R.string.update,
			isEnabled = true,
		)

		isInstalledPrivately -> RepoExtensionAction(
			icon = R.drawable.ic_delete,
			label = R.string.remove,
			isEnabled = true,
		)

		isInstalledExternally -> RepoExtensionAction(
			icon = R.drawable.ic_check,
			label = R.string.extension_installed_externally,
			isEnabled = false,
		)

		else -> RepoExtensionAction(
			icon = R.drawable.ic_add,
			label = R.string.add,
			isEnabled = true,
		)
	}
}

private data class RepoExtensionAction(
	@DrawableRes val icon: Int,
	@StringRes val label: Int,
	val isEnabled: Boolean,
)
