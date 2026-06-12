package com.kosen.reader.explore.ui

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.model.LocalMangaSource
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.parser.external.ExternalMangaSource
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BaseFragment
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.dialog.BigButtonsAlertDialog
import com.kosen.reader.core.ui.list.ListSelectionController
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.util.RecyclerViewOwner
import com.kosen.reader.core.ui.util.ReversibleActionObserver
import com.kosen.reader.core.ui.util.SpanSizeResolver
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.core.util.ext.addMenuProvider
import com.kosen.reader.core.util.ext.consumeAllSystemBarsInsets
import com.kosen.reader.core.util.ext.findAppCompatDelegate
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.systemBarsInsets
import com.kosen.reader.databinding.FragmentExploreBinding
import com.kosen.reader.databinding.FragmentExploreHomeBinding
import com.kosen.reader.explore.ui.adapter.ExploreAdapter
import com.kosen.reader.explore.ui.adapter.ExploreListEventListener
import com.kosen.reader.explore.ui.model.MangaSourceItem
import com.kosen.reader.explore.ui.model.RecommendationsItem
import com.kosen.reader.explore.ui.model.SuggestionsEmptyItem
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.history.ui.ContinueReadingSection
import com.kosen.reader.history.ui.ContinueReadingViewModel
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.list.ui.adapter.TypedListSpacingDecoration
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.suggestions.domain.SuggestionsListQuickFilter
import com.kosen.reader.suggestions.domain.getSuggestionsFilterChipTitle
import com.kosen.reader.suggestions.ui.SuggestionsWorker

@AndroidEntryPoint
open class ExploreFragment :
	BaseFragment<FragmentExploreBinding>(),
	RecyclerViewOwner,
	ExploreListEventListener,
	OnListItemClickListener<MangaSourceItem>, ListSelectionController.Callback {

	@Inject
	lateinit var historyRepository: HistoryRepository

	@Inject
	lateinit var suggestionsQuickFilter: SuggestionsListQuickFilter

	@Inject
	lateinit var suggestionsScheduler: SuggestionsWorker.Scheduler

	@Inject
	lateinit var settings: AppSettings

	protected val viewModel by viewModels<ExploreViewModel>()
	private val continueReadingViewModel by viewModels<ContinueReadingViewModel>()
	protected var exploreAdapter: BaseListAdapter<ListModel>? = null
	private var sourceSelectionController: ListSelectionController? = null
	private var homeBinding: FragmentExploreHomeBinding? = null
	private var continueReadingSection: ContinueReadingSection? = null
	private var isFilterLoading = false
	private var appliedFilters: Set<ListFilterOption> = emptySet()

	override val recyclerView: RecyclerView?
		get() = viewBinding?.recyclerView

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentExploreBinding {
		val home = FragmentExploreHomeBinding.inflate(inflater, container, false)
		homeBinding = home
		return home.exploreContainer
	}

	override fun onCreateFragmentRootView(binding: FragmentExploreBinding): View {
		return homeBinding?.root ?: binding.root
	}

	protected open fun createExploreAdapter(): BaseListAdapter<ListModel> = ExploreAdapter(this, this) { manga, _ ->
		router.openDetails(manga)
	}

	protected open fun mapExploreContent(content: List<ListModel>): List<ListModel> {
		val recommendations = content.filterIsInstance<RecommendationsItem>().firstOrNull()
		val suggestionsCount = recommendations?.manga?.size ?: 0
		val hasSuggestionsHeader = content.any { it is ListHeader && it.payload == R.id.nav_suggestions }
		val hasRecommendations = suggestionsCount > 0
		if (hasRecommendations) {
			isFilterLoading = false
			loadRecommendationProgress(recommendations!!.manga.map { it.manga.id })
		}
		val result = ArrayList<ListModel>(content.size + 2)
		for (item in content) {
			when {
				item is ListHeader && item.payload == R.id.nav_suggestions -> {
					val badge = when {
						isFilterLoading -> item.badge
						suggestionsCount > 0 || appliedFilters.isNotEmpty() ->
							getString(R.string.suggestions_count, suggestionsCount)
						else -> item.badge
					}
					result += ListHeader(
						textRes = R.string.suggestions,
						buttonTextRes = R.string.more,
						filterButtonTextRes = R.string.filter,
						payload = R.id.nav_suggestions,
						badge = badge,
					)
					if (isFilterLoading && !hasRecommendations) {
						result += LoadingState
					}
				}
				item is RecommendationsItem -> result += item
				item is LoadingState && hasSuggestionsHeader && isFilterLoading -> Unit
				else -> result += item
			}
		}
		if (hasSuggestionsHeader && !hasRecommendations && !isFilterLoading) {
			result += SuggestionsEmptyItem(buildEmptyFilterHint())
		}
		return result
	}

	override fun onViewBindingCreated(binding: FragmentExploreBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		exploreAdapter = createExploreAdapter()
		sourceSelectionController = ListSelectionController(
			appCompatDelegate = checkNotNull(findAppCompatDelegate()),
			decoration = SourceSelectionDecoration(binding.root.context),
			registryOwner = this,
			callback = this,
		)
		with(binding.recyclerView) {
			adapter = exploreAdapter
			setHasFixedSize(true)
			SpanSizeResolver(this, resources.getDimensionPixelSize(R.dimen.explore_grid_width)).attach()
			addItemDecoration(TypedListSpacingDecoration(context, false))
			checkNotNull(sourceSelectionController).attachToRecyclerView(this)
		}
		addMenuProvider(ExploreMenuProvider(router))
		viewModel.content.observe(viewLifecycleOwner, Lifecycle.State.STARTED) { content ->
			checkNotNull(exploreAdapter).items = mapExploreContent(content)
		}
		viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(binding.recyclerView, this))
		viewModel.onOpenManga.observeEvent(viewLifecycleOwner, ::onOpenManga)
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.recyclerView))
		viewModel.isGrid.observe(viewLifecycleOwner, Lifecycle.State.STARTED, ::onGridModeChanged)
		viewModel.onShowSuggestionsTip.observeEvent(viewLifecycleOwner) {
			showSuggestionsTip()
		}
		val home = homeBinding ?: return
		home.chipsActiveFilters.onChipClickListener = ChipsView.OnChipClickListener { _, data ->
			(data as? ListFilterOption)?.let { suggestionsQuickFilter.toggleFilterOption(it) }
		}
		suggestionsQuickFilter.appliedOptions.observe(viewLifecycleOwner, Lifecycle.State.STARTED) { filters ->
			if (filters != appliedFilters) {
				isFilterLoading = true
				appliedFilters = filters
			}
			bindActiveFilterChips(filters)
		}
		if (settings.isTipEnabled(TIP_EXPLORE)) {
			showExploreOnboarding()
		}
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

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val basePadding = v.resources.getDimensionPixelOffset(R.dimen.list_spacing_normal)
		viewBinding?.recyclerView?.setPadding(
			barsInsets.left + basePadding,
			basePadding,
			barsInsets.right + basePadding,
			barsInsets.bottom + basePadding,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onDestroyView() {
		continueReadingSection?.detach()
		continueReadingSection = null
		homeBinding = null
		super.onDestroyView()
		sourceSelectionController = null
		exploreAdapter = null
	}

	override fun onListHeaderClick(item: ListHeader, view: View) {
		if (item.payload == R.id.nav_suggestions) {
			router.openSuggestions()
		} else if (viewModel.isAllSourcesEnabled.value) {
			router.openManageSources()
		} else {
			router.openSourcesCatalog()
		}
	}

	override fun onListHeaderFilterClick(item: ListHeader, view: View) {
		if (item.payload == R.id.nav_suggestions) {
			router.showSuggestionsFilterSheet()
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_local -> router.openList(LocalMangaSource, null, null)
			R.id.button_bookmarks -> router.openBookmarks()
			R.id.button_more -> router.openSuggestions()
			R.id.button_downloads -> router.openDownloads()
			R.id.button_random -> viewModel.openRandom()
			R.id.button_refresh -> lifecycleScope.launch { suggestionsScheduler.schedule() }
		}
	}

	override fun onItemClick(item: MangaSourceItem, view: View) {
		if (sourceSelectionController?.onItemClick(item.id) == true) {
			return
		}
		viewModel.rememberSourceForType(item.source.mangaSource)
		router.openList(item.source, null, null)
	}

	override fun onItemLongClick(item: MangaSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemLongClick(view, item.id) == true
	}

	override fun onItemContextClick(item: MangaSourceItem, view: View): Boolean {
		return sourceSelectionController?.onItemContextClick(view, item.id) == true
	}

	override fun onRetryClick(error: Throwable) = Unit

	override fun onEmptyActionClick() = router.openSourcesCatalog()

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding?.recyclerView?.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_source, menu)
		return true
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		val isSingleSelection = selectedSources.size == 1
		menu.findItem(R.id.action_settings).isVisible = isSingleSelection
		menu.findItem(R.id.action_shortcut).isVisible = isSingleSelection
		menu.findItem(R.id.action_pin).isVisible = selectedSources.all { !it.isPinned }
		menu.findItem(R.id.action_unpin).isVisible = selectedSources.all { it.isPinned }
		menu.findItem(R.id.action_disable)?.isVisible = !viewModel.isAllSourcesEnabled.value &&
			selectedSources.all { it.mangaSource is MangaParserSource }
		menu.findItem(R.id.action_delete)?.isVisible = selectedSources.all { it.mangaSource is ExternalMangaSource }
		return super.onPrepareActionMode(controller, mode, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		val selectedSources = viewModel.sourcesSnapshot(controller.peekCheckedIds())
		if (selectedSources.isEmpty()) {
			return false
		}
		when (item.itemId) {
			R.id.action_settings -> {
				val source = selectedSources.singleOrNull() ?: return false
				router.openSourceSettings(source)
				mode?.finish()
			}

			R.id.action_disable -> {
				viewModel.disableSources(selectedSources)
				mode?.finish()
			}

			R.id.action_delete -> {
				selectedSources.forEach {
					(it.mangaSource as? ExternalMangaSource)?.let { uninstallExternalSource(it) }
				}
				mode?.finish()
			}

			R.id.action_shortcut -> {
				val source = selectedSources.singleOrNull() ?: return false
				viewModel.requestPinShortcut(source)
				mode?.finish()
			}

			R.id.action_pin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = true)
				mode?.finish()
			}

			R.id.action_unpin -> {
				viewModel.setSourcesPinned(selectedSources, isPinned = false)
				mode?.finish()
			}

			else -> return false
		}
		return true
	}

	private fun loadRecommendationProgress(ids: List<Long>) {
		if (ids.isEmpty()) {
			return
		}
		lifecycleScope.launch(Dispatchers.Default) {
			val map = historyRepository.getProgressMap(ids, settings.progressIndicatorMode)
			withContext(Dispatchers.Main) {
				(exploreAdapter as? ExploreAdapter)?.progressMap = map
				exploreAdapter?.notifyDataSetChanged()
			}
		}
	}

	private fun buildEmptyFilterHint(): String? {
		if (appliedFilters.isEmpty()) {
			return null
		}
		val first = appliedFilters.firstOrNull() ?: return null
		return getString(
			R.string.suggestions_empty_filtered,
			getSuggestionsFilterChipTitle(requireContext(), first),
		)
	}

	private fun bindActiveFilterChips(filters: Set<ListFilterOption>) {
		val home = homeBinding ?: return
		val chips = filters.map { option ->
			ChipsView.ChipModel(
				title = getSuggestionsFilterChipTitle(requireContext(), option),
				isChecked = true,
				isCloseable = true,
				data = option,
			)
		}
		home.chipsActiveFilters.isVisible = chips.isNotEmpty()
		home.chipsActiveFilters.setChips(chips)
	}

	private fun showExploreOnboarding() {
		val steps = listOf(
			R.string.onboarding_explore_step1_title to R.string.onboarding_explore_step1_message,
			R.string.onboarding_explore_step2_title to R.string.onboarding_explore_step2_message,
			R.string.onboarding_explore_step3_title to R.string.onboarding_explore_step3_message,
		)
		fun showStep(index: Int) {
			if (index >= steps.size) {
				settings.closeTip(TIP_EXPLORE)
				return
			}
			val (title, message) = steps[index]
			MaterialAlertDialogBuilder(requireContext())
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(
					if (index == steps.lastIndex) android.R.string.ok else R.string.next,
				) { _, _ -> showStep(index + 1) }
				.setNegativeButton(R.string.skip) { _, _ -> settings.closeTip(TIP_EXPLORE) }
				.show()
		}
		showStep(0)
	}

	private fun onOpenManga(manga: Manga) {
		router.openDetails(manga)
	}

	private fun onGridModeChanged(isGrid: Boolean) {
		val recyclerView = requireViewBinding().recyclerView
		val currentLayoutManager = recyclerView.layoutManager
		val matchesCurrentMode = when {
			isGrid -> currentLayoutManager is GridLayoutManager
			else -> currentLayoutManager is LinearLayoutManager && currentLayoutManager !is GridLayoutManager
		}
		if (matchesCurrentMode) {
			return
		}
		val savedState = currentLayoutManager?.onSaveInstanceState()
		recyclerView.layoutManager = if (isGrid) {
			GridLayoutManager(requireContext(), 4).also { lm ->
				lm.spanSizeLookup = ExploreGridSpanSizeLookup(checkNotNull(exploreAdapter), lm)
			}
		} else {
			LinearLayoutManager(requireContext())
		}
		savedState?.let {
			recyclerView.layoutManager?.onRestoreInstanceState(it)
		}
	}

	private fun showSuggestionsTip() {
		val listener = DialogInterface.OnClickListener { _, which ->
			viewModel.respondSuggestionTip(which == DialogInterface.BUTTON_POSITIVE)
		}
		BigButtonsAlertDialog.Builder(requireContext())
			.setIcon(R.drawable.ic_suggestion)
			.setTitle(R.string.suggestions_enable_prompt)
			.setPositiveButton(R.string.enable, listener)
			.setNegativeButton(R.string.no_thanks, listener)
			.create()
			.show()
	}

	private fun uninstallExternalSource(source: ExternalMangaSource) {
		val uri = Uri.fromParts("package", source.packageName, null)
		val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			Intent.ACTION_DELETE
		} else {
			@Suppress("DEPRECATION")
			Intent.ACTION_UNINSTALL_PACKAGE
		}
		context?.startActivity(Intent(action, uri))
	}

	private companion object {
		const val TIP_EXPLORE = "explore_tour"
	}
}
