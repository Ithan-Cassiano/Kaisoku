package com.kosen.reader.list.ui.adapter

import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.core.ui.list.AdapterDelegateClickListenerAdapter
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.ItemMangaListDetailsBinding
import com.kosen.reader.list.ui.ListModelDiffCallback
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaDetailedListModel

fun mangaListDetailedItemAD(
	clickListener: MangaDetailsClickListener,
) = adapterDelegateViewBinding<MangaDetailedListModel, ListModel, ItemMangaListDetailsBinding>(
	{ inflater, parent -> ItemMangaListDetailsBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener)
		.attach(itemView)

	bind { payloads ->
		binding.textViewTitle.text = item.title
		binding.textViewAuthor.textAndVisible = item.manga.authors.joinToString(", ")
		binding.progressView.setProgress(
			value = item.progress,
			animate = ListModelDiffCallback.PAYLOAD_PROGRESS_CHANGED in payloads,
		)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.textViewTags.text = item.tags.joinToString(separator = ", ") { it.title ?: "" }
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
	}
}
