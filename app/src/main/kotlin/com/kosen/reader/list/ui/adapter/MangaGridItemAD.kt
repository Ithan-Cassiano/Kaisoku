package com.kosen.reader.list.ui.adapter

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import com.kosen.reader.R
import com.kosen.reader.core.ui.list.AdapterDelegateClickListenerAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.util.ext.setTooltipCompat
import com.kosen.reader.databinding.ItemMangaGridBinding
import com.kosen.reader.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaGridModel
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.list.ui.size.ItemSizeResolver
import kotlin.math.abs

private val coverBlurEffect: RenderEffect? by lazy {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
	} else {
		null
	}
}

fun mangaGridItemAD(
	sizeResolver: ItemSizeResolver,
	clickListener: OnListItemClickListener<MangaListModel>,
) = adapterDelegateViewBinding<MangaGridModel, ListModel, ItemMangaGridBinding>(
	{ inflater, parent -> ItemMangaGridBinding.inflate(inflater, parent, false) },
) {

	AdapterDelegateClickListenerAdapter(this, clickListener).attach(itemView)
	sizeResolver.attachToView(itemView, binding.textViewTitle, binding.progressView)

	bind { payloads ->
		itemView.setTooltipCompat(item.getSummary(context))
		binding.textViewTitle.text = item.title
		binding.textViewTitle.isVisible = !item.isTitleHidden
		binding.progressView.setProgress(item.progress, PAYLOAD_PROGRESS_CHANGED in payloads)
		with(binding.iconsView) {
			clearIcons()
			if (item.isSaved) addIcon(R.drawable.ic_storage)
			if (item.isFavorite) addIcon(R.drawable.ic_heart_outline)
			if (item.isHiddenFromMain) {
				addIcon(R.drawable.ic_incognito)
				itemView.setTooltipCompat(context.getString(R.string.hidden_from_main_history))
			}
			isVisible = iconsCount > 0
		}
		binding.imageViewCover.setImageAsync(item.coverUrl, item.manga)
		binding.badge.number = item.counter
		binding.badge.isVisible = item.counter > 0
		binding.bindCoverBlur(item.isCoverBlurred)
	}
}

private fun ItemMangaGridBinding.applyCoverBlurEffect(effect: RenderEffect?) {
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		imageViewCover.setRenderEffect(effect)
	}
}

private fun ItemMangaGridBinding.bindCoverBlur(isBlurred: Boolean) {
	if (!isBlurred) {
		blurOverlay.isVisible = false
		blurOverlay.setOnTouchListener(null)
		applyCoverBlurEffect(null)
		return
	}
	applyCoverBlurEffect(coverBlurEffect)
	blurOverlay.isVisible = true
	blurOverlay.alpha = 1f
	val touchSlop = ViewConfiguration.get(root.context).scaledTouchSlop
	var downY = 0f
	var downX = 0f
	var isRevealing = false
	blurOverlay.setOnTouchListener { overlay, event ->
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				downY = event.y
				downX = event.x
				isRevealing = true
				applyCoverBlurEffect(null)
				overlay.parent?.requestDisallowInterceptTouchEvent(true)
				true
			}
			MotionEvent.ACTION_MOVE -> {
				if (!isRevealing) {
					return@setOnTouchListener false
				}
				val dy = abs(event.y - downY)
				val dx = abs(event.x - downX)
				if (dy > touchSlop || dx > touchSlop) {
					applyCoverBlurEffect(coverBlurEffect)
					isRevealing = false
					overlay.parent?.requestDisallowInterceptTouchEvent(false)
					false
				} else {
					true
				}
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				val wasTap = isRevealing &&
					abs(event.y - downY) <= touchSlop &&
					abs(event.x - downX) <= touchSlop
				applyCoverBlurEffect(coverBlurEffect)
				isRevealing = false
				overlay.parent?.requestDisallowInterceptTouchEvent(false)
				if (wasTap) {
					root.performClick()
				}
				false
			}
			else -> false
		}
	}
}
