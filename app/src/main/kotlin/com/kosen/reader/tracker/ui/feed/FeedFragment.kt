package com.kosen.reader.tracker.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.drop
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.BaseFragment
import com.kosen.reader.core.ui.list.PaginationScrollListener
import com.kosen.reader.core.ui.list.RecyclerScrollKeeper
import com.kosen.reader.core.ui.util.MenuInvalidator
import com.kosen.reader.core.ui.util.RecyclerViewOwner
import com.kosen.reader.core.ui.util.ReversibleActionObserver
import com.kosen.reader.core.ui.widgets.TipView
import com.kosen.reader.core.util.ext.addMenuProvider
import com.kosen.reader.core.util.ext.consumeAll
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.databinding.FragmentFeedHomeBinding
import com.kosen.reader.databinding.FragmentListBinding
import com.kosen.reader.history.ui.ContinueReadingSection
import com.kosen.reader.history.ui.ContinueReadingViewModel
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.adapter.MangaListListener
import com.kosen.reader.list.ui.adapter.TypedListSpacingDecoration
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.list.ui.size.StaticItemSizeResolver
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.tracker.ui.feed.adapter.FeedAdapter
import javax.inject.Inject

@AndroidEntryPoint
class FeedFragment :
	BaseFragment<FragmentListBinding>(),
	PaginationScrollListener.Callback,
	RecyclerViewOwner,
	MangaListListener,
	SwipeRefreshLayout.OnRefreshListener {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel by viewModels<FeedViewModel>()
	private val continueReadingViewModel by viewModels<ContinueReadingViewModel>()
	private var homeBinding: FragmentFeedHomeBinding? = null
	private var continueReadingSection: ContinueReadingSection? = null

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentListBinding {
		val home = FragmentFeedHomeBinding.inflate(inflater, container, false)
		homeBinding = home
		return home.listContainer
	}

	override fun onCreateFragmentRootView(binding: FragmentListBinding): View {
		return homeBinding?.root ?: binding.root
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val sizeResolver = StaticItemSizeResolver(resources.getDimensionPixelSize(R.dimen.smaller_grid_width))
		val feedAdapter = FeedAdapter(this, sizeResolver) { item, v ->
			viewModel.onItemClick(item)
			router.openDetails(item.toMangaWithOverride())
		}
		with(binding.recyclerView) {
			val paddingVertical = resources.getDimensionPixelSize(R.dimen.list_spacing_normal)
			setPadding(0, paddingVertical, 0, paddingVertical)
			layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
			adapter = feedAdapter
			setHasFixedSize(true)
			addOnScrollListener(PaginationScrollListener(4, this@FeedFragment))
			addItemDecoration(TypedListSpacingDecoration(context, true))
			RecyclerScrollKeeper(this).attach()
		}
		binding.swipeRefreshLayout.setOnRefreshListener(this)
		addMenuProvider(FeedMenuProvider(binding.recyclerView, viewModel))

		viewModel.isHeaderEnabled.drop(1).observe(viewLifecycleOwner, Lifecycle.State.STARTED, MenuInvalidator(requireActivity()))
		viewModel.content.observe(viewLifecycleOwner, Lifecycle.State.STARTED, feedAdapter)
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isRunning.observe(viewLifecycleOwner, Lifecycle.State.STARTED, this::onIsTrackerRunningChanged)
		val home = homeBinding ?: return
		continueReadingSection = ContinueReadingSection(
			panel = home.continueReadingPanel,
			viewModel = continueReadingViewModel,
			lifecycleOwner = viewLifecycleOwner,
			snackbarHost = binding.recyclerView,
			onMangaClick = { manga -> router.openDetails(manga) },
		).also { section ->
			section.attachTo(binding.recyclerView)
		}
	}

	override fun onDestroyView() {
		continueReadingSection?.detach()
		continueReadingSection = null
		homeBinding = null
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val paddingVertical = resources.getDimensionPixelSize(R.dimen.list_spacing_normal)
		viewBinding?.recyclerView?.setPadding(
			left = barsInsets.left,
			top = paddingVertical,
			right = barsInsets.right,
			bottom = barsInsets.bottom + paddingVertical,
		)
		return insets.consumeAll(typeMask)
	}

	override fun onRefresh() {
		viewModel.update()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = viewModel.toggleFilterOption(option)

	override fun onRetryClick(error: Throwable) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = Unit

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onListHeaderClick(item: ListHeader, view: View) {
		router.openMangaUpdates()
	}

	private fun onIsTrackerRunningChanged(isRunning: Boolean) {
		requireViewBinding().swipeRefreshLayout.isRefreshing = isRunning
	}

	override fun onScrolledToEnd() {
		viewModel.requestMoreItems()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		router.openDetails(item.toMangaWithOverride())
	}

	override fun onReadClick(manga: Manga, view: View) = Unit

	override fun onTagClick(manga: Manga, tag: MangaTag, view: View) = Unit
}
