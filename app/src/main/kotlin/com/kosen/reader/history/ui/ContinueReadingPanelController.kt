package com.kosen.reader.history.ui

import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.kosen.reader.databinding.IncludeContinueReadingPanelBinding

internal class ContinueReadingPanelController(
	private val binding: IncludeContinueReadingPanelBinding,
) {
	private var isExpanded = true

	fun attachScrollBehavior(recyclerView: RecyclerView): RecyclerView.OnScrollListener {
		val listener = object : RecyclerView.OnScrollListener() {
			private var accumulatedDy = 0

			override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
				if (!binding.continueHeader.isVisible) {
					return
				}
				if (rv.computeVerticalScrollOffset() <= 0) {
					accumulatedDy = 0
					if (!isExpanded) {
						expand(animate = true)
					}
					return
				}
				accumulatedDy += dy
				when {
					dy > 0 && accumulatedDy > COLLAPSE_THRESHOLD && isExpanded -> collapse(animate = true)
					dy < 0 && accumulatedDy < -EXPAND_THRESHOLD && !isExpanded -> expand(animate = true)
				}
				if (dy != 0 && kotlin.math.abs(accumulatedDy) > COLLAPSE_THRESHOLD * 2) {
					accumulatedDy = accumulatedDy.coerceIn(-EXPAND_THRESHOLD, COLLAPSE_THRESHOLD)
				}
			}
		}
		recyclerView.addOnScrollListener(listener)
		return listener
	}

	fun setHasItems(hasItems: Boolean, itemCount: Int) {
		binding.continueHeader.isVisible = hasItems && isExpanded
		binding.chipContinueCollapsed.isVisible = hasItems && !isExpanded
		if (!hasItems) {
			isExpanded = true
		}
	}

	fun setCollapsedClickListener(listener: () -> Unit) {
		binding.chipContinueCollapsed.setOnClickListener { listener() }
	}

	fun expand(animate: Boolean = true) {
		if (isExpanded) {
			return
		}
		isExpanded = true
		binding.chipContinueCollapsed.isVisible = false
		binding.continueHeader.isVisible = true
		if (!animate) {
			return
		}
		val header = binding.continueHeader
		header.alpha = 0f
		header.translationY = -header.height * 0.2f
		header.animate().alpha(1f).translationY(0f).setDuration(ANIM_MS).start()
	}

	private fun collapse(animate: Boolean) {
		if (!isExpanded) {
			return
		}
		isExpanded = false
		val header = binding.continueHeader
		if (!animate) {
			header.isVisible = false
			binding.chipContinueCollapsed.isVisible = true
			return
		}
		header.animate()
			.alpha(0f)
			.translationY(-header.height * 0.35f)
			.setDuration(ANIM_MS)
			.withEndAction {
				header.isVisible = false
				header.alpha = 1f
				header.translationY = 0f
				binding.chipContinueCollapsed.isVisible = true
				binding.chipContinueCollapsed.alpha = 0f
				binding.chipContinueCollapsed.animate().alpha(1f).setDuration(ANIM_MS).start()
			}
			.start()
	}

	private companion object {
		const val COLLAPSE_THRESHOLD = 48
		const val EXPAND_THRESHOLD = 32
		const val ANIM_MS = 200L
	}
}
