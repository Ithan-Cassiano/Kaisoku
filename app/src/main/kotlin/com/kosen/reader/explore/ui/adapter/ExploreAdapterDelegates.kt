package com.kosen.reader.explore.ui.adapter

import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import coil3.size.Size
import com.kosen.reader.R
import com.kosen.reader.core.model.getSummary
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.AdapterDelegateClickListenerAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.util.ext.drawableStart
import com.kosen.reader.core.util.ext.recyclerView
import com.kosen.reader.core.util.ext.setProgressIcon
import com.kosen.reader.core.util.ext.setTooltipCompat
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.ItemExploreButtonsBinding
import com.kosen.reader.databinding.ItemExploreSourceGridBinding
import com.kosen.reader.databinding.ItemExploreSourceListBinding
import com.kosen.reader.databinding.ItemRecommendationBinding
import com.kosen.reader.databinding.ItemRecommendationMangaBinding
import com.kosen.reader.databinding.ItemSuggestionsEmptyBinding
import com.kosen.reader.explore.domain.SourceHealthTracker
import com.kosen.reader.explore.ui.model.ExploreButtons
import com.kosen.reader.explore.ui.model.MangaSourceItem
import com.kosen.reader.explore.ui.model.RecommendationsItem
import com.kosen.reader.explore.ui.model.SuggestionsEmptyItem
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaCompactListModel
import com.kosen.reader.parsers.model.Manga
import kotlin.math.roundToInt

fun exploreButtonsAD(
	clickListener: View.OnClickListener,
) = adapterDelegateViewBinding<ExploreButtons, ListModel, ItemExploreButtonsBinding>(
	{ layoutInflater, parent -> ItemExploreButtonsBinding.inflate(layoutInflater, parent, false) },
) {
	binding.buttonBookmarks.setOnClickListener(clickListener)
	binding.buttonDownloads.setOnClickListener(clickListener)
	binding.buttonLocal.setOnClickListener(clickListener)
	binding.buttonRandom.setOnClickListener(clickListener)
	bind {
		if (item.isRandomLoading) {
			binding.buttonRandom.setProgressIcon()
		} else {
			binding.buttonRandom.setIconResource(R.drawable.ic_dice)
		}
		binding.buttonRandom.isClickable = !item.isRandomLoading
	}
}

fun exploreRecommendationItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
	progressProvider: () -> Map<Long, ReadingProgress?>,
) = adapterDelegateViewBinding<RecommendationsItem, ListModel, ItemRecommendationBinding>(
	{ layoutInflater, parent -> ItemRecommendationBinding.inflate(layoutInflater, parent, false) },
) {
	val adapter = BaseListAdapter<MangaCompactListModel>()
		.addDelegate(ListItemType.MANGA_LIST, recommendationMangaItemAD(itemClickListener, progressProvider))
	binding.pager.adapter = adapter
	binding.pager.recyclerView?.isNestedScrollingEnabled = false
	binding.dots.bindToViewPager(binding.pager)
	bind {
		adapter.items = item.manga
	}
}

fun recommendationMangaItemAD(
	itemClickListener: OnListItemClickListener<Manga>,
	progressProvider: () -> Map<Long, ReadingProgress?>,
) = adapterDelegateViewBinding<MangaCompactListModel, MangaCompactListModel, ItemRecommendationMangaBinding>(
	{ layoutInflater, parent -> ItemRecommendationMangaBinding.inflate(layoutInflater, parent, false) },
) {
	binding.root.setOnClickListener { v ->
		itemClickListener.onItemClick(item.manga, v)
	}
	bind {
		binding.textViewTitle.text = item.manga.title
		binding.textViewSubtitle.textAndVisible = item.subtitle
		val height = context.resources.getDimensionPixelSize(R.dimen.recommendation_item_height)
		val width = context.resources.displayMetrics.widthPixels
		binding.imageViewCover.exactImageSize = Size(width * 2, height * 2)
		binding.imageViewCover.setImageAsync(item.manga.coverUrl, item.manga.source)
		binding.imageViewSource.isVisible = true
		binding.imageViewSource.setImageAsync(item.manga.source)
		val progress = progressProvider()[item.manga.id]
		val percent = progress?.percent ?: 0f
		if (percent in 0.01f..0.99f) {
			binding.progressBar.isVisible = true
			binding.progressBar.max = 100
			binding.progressBar.setProgressCompat((percent * 100).roundToInt(), false)
		} else {
			binding.progressBar.isVisible = false
		}
	}
}

fun suggestionsEmptyAD(
	listener: ExploreListEventListener,
) = adapterDelegateViewBinding<SuggestionsEmptyItem, ListModel, ItemSuggestionsEmptyBinding>(
	{ layoutInflater, parent -> ItemSuggestionsEmptyBinding.inflate(layoutInflater, parent, false) },
) {
	binding.buttonRefresh.setOnClickListener {
		listener.onClick(it)
	}
	binding.buttonSources.setOnClickListener { listener.onEmptyActionClick() }
	bind {
		binding.textSecondary.textAndVisible = item.filterHint
	}
}

fun exploreSourceListItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceListBinding>(
	{ layoutInflater, parent -> ItemExploreSourceListBinding.inflate(layoutInflater, parent, false) },
	on = { item, _, _ -> item is MangaSourceItem && !item.isGrid },
) {
	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	bind {
		binding.textViewTitle.text = item.source.getTitle(context)
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		val status = SourceHealthTracker.getStatus(item.source)
		binding.textViewSubtitle.text = item.source.getSummary(context)
		binding.imageViewIcon.setImageAsync(item.source)
		bindHealthIndicator(binding.viewHealthIndicator, status)
	}
}

fun exploreSourceGridItemAD(
	listener: OnListItemClickListener<MangaSourceItem>,
) = adapterDelegateViewBinding<MangaSourceItem, ListModel, ItemExploreSourceGridBinding>(
	{ layoutInflater, parent -> ItemExploreSourceGridBinding.inflate(layoutInflater, parent, false) },
	on = { item, _, _ -> item is MangaSourceItem && item.isGrid },
) {
	AdapterDelegateClickListenerAdapter(this, listener).attach(itemView)
	val iconPinned = ContextCompat.getDrawable(context, R.drawable.ic_pin_small)
	bind {
		val title = item.source.getTitle(context)
		val status = SourceHealthTracker.getStatus(item.source)
		val statusLabel = when (status) {
			SourceHealthTracker.Status.OK -> context.getString(R.string.source_status_ok)
			SourceHealthTracker.Status.UNSTABLE -> context.getString(R.string.source_status_unstable)
			SourceHealthTracker.Status.OFFLINE -> context.getString(R.string.source_status_offline)
		}
		itemView.setTooltipCompat(
			buildSpannedString {
				bold { append(title) }
				appendLine()
				append(item.source.getSummary(context))
				appendLine()
				append(statusLabel)
			},
		)
		binding.textViewTitle.text = title
		binding.textViewTitle.drawableStart = if (item.source.isPinned) iconPinned else null
		binding.imageViewIcon.setImageAsync(item.source)
	}
}

private fun bindHealthIndicator(view: View, status: SourceHealthTracker.Status) {
	val color = when (status) {
		SourceHealthTracker.Status.OK -> 0xFF4CAF50.toInt()
		SourceHealthTracker.Status.UNSTABLE -> 0xFFFFC107.toInt()
		SourceHealthTracker.Status.OFFLINE -> 0xFFF44336.toInt()
	}
	view.background?.mutate()?.setTint(color)
	view.background?.alpha = 255
	view.isVisible = true
}
