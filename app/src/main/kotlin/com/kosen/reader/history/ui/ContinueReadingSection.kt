package com.kosen.reader.history.ui

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.kosen.reader.BuildConfig
import com.kosen.reader.R
import com.kosen.reader.core.prefs.ProgressIndicatorMode
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.databinding.IncludeContinueReadingPanelBinding
import com.kosen.reader.image.ui.CoverImageView
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.model.Manga

class ContinueReadingSection(
	private val panel: IncludeContinueReadingPanelBinding,
	private val viewModel: ContinueReadingViewModel,
	private val lifecycleOwner: LifecycleOwner,
	private val snackbarHost: android.view.View,
	private val onMangaClick: (Manga) -> Unit,
) {

	private val adapter = ContinueReadingCarouselAdapter(
		onClick = onMangaClick,
		onLongClick = { manga -> hideFromMainHistory(manga) },
		canHide = { manga -> viewModel.canHideFromMainHistory(manga) },
	)
	private var panelController: ContinueReadingPanelController? = null
	private var scrollListener: RecyclerView.OnScrollListener? = null
	private var attachedRecyclerView: RecyclerView? = null
	private val uxHeroBinder = if (BuildConfig.UX_REDESIGN) UxContinueReadingHeroBinder(panel.root) else null

	init {
		viewModel.continueReadingItems.observe(lifecycleOwner, Lifecycle.State.STARTED) { items ->
			panelController?.setHasItems(items.isNotEmpty(), items.size)
			uxHeroBinder?.bind(items, onMangaClick) { manga -> hideFromMainHistory(manga) }
			val carouselItems = if (BuildConfig.UX_REDESIGN && items.size > 1) {
				items.drop(1)
			} else if (BuildConfig.UX_REDESIGN) {
				emptyList()
			} else {
				items
			}
			adapter.submit(carouselItems)
			if (BuildConfig.UX_REDESIGN) {
				val showCarousel = carouselItems.isNotEmpty()
				panel.root.findViewById<View>(R.id.section_for_you)?.isVisible = showCarousel
				panel.continueCarousel.isVisible = showCarousel
			}
		}
	}

	fun attachTo(recyclerView: RecyclerView) {
		if (attachedRecyclerView === recyclerView) {
			return
		}
		detach()
		panel.continueCarousel.layoutManager = LinearLayoutManager(
			recyclerView.context,
			LinearLayoutManager.HORIZONTAL,
			false,
		)
		panel.continueCarousel.adapter = adapter
		panelController = ContinueReadingPanelController(panel).also { controller ->
			controller.setCollapsedClickListener { controller.expand(animate = true) }
			scrollListener = controller.attachScrollBehavior(recyclerView)
			val items = viewModel.continueReadingItems.value
			controller.setHasItems(items.isNotEmpty(), items.size)
		}
		attachedRecyclerView = recyclerView
	}

	fun detach() {
		scrollListener?.let { listener ->
			attachedRecyclerView?.removeOnScrollListener(listener)
		}
		scrollListener = null
		panelController = null
		attachedRecyclerView = null
	}

	private fun hideFromMainHistory(manga: Manga) {
		if (!viewModel.canHideFromMainHistory(manga)) {
			return
		}
		viewModel.hideFromMainHistory(setOf(manga))
		Snackbar.make(snackbarHost, R.string.hidden_from_main_history, Snackbar.LENGTH_SHORT).show()
	}
}
