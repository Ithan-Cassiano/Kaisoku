package com.kosen.reader.search.ui.multi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.core.model.parcelable.ParcelableManga
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.ui.dialog.buildAlertDialog
import com.kosen.reader.core.ui.list.ListSelectionController
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.widgets.TipView
import com.kosen.reader.core.util.ShareHelper
import com.kosen.reader.core.util.ext.consumeAllSystemBarsInsets
import com.kosen.reader.core.util.ext.invalidateNestedItemDecorations
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.systemBarsInsets
import com.kosen.reader.databinding.ActivitySearchBinding
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.MangaSelectionDecoration
import com.kosen.reader.list.ui.adapter.MangaListListener
import com.kosen.reader.list.ui.adapter.TypedListSpacingDecoration
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.list.ui.size.DynamicItemSizeResolver
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.search.domain.SearchKind
import com.kosen.reader.search.ui.multi.adapter.SearchAdapter
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity :
	BaseActivity<ActivitySearchBinding>(),
	MangaListListener,
	ListSelectionController.Callback {

	@Inject
	lateinit var settings: AppSettings

	private val viewModel by viewModels<SearchViewModel>()
	private lateinit var selectionController: ListSelectionController
	private var pickMode: Boolean = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySearchBinding.inflate(layoutInflater))
		pickMode = intent.getBooleanExtra(AppRouter.KEY_PICK_MODE, false)
		title = when (viewModel.kind) {
			SearchKind.SIMPLE,
			SearchKind.TITLE -> viewModel.query

			SearchKind.AUTHOR -> getString(
				R.string.inline_preference_pattern,
				getString(R.string.author),
				viewModel.query,
			)

			SearchKind.TAG -> getString(R.string.inline_preference_pattern, getString(R.string.genre), viewModel.query)
		}

		val itemClickListener = OnListItemClickListener<SearchResultsListModel> { item, view ->
			if (item.listFilter == null) {
				router.openSearch(item.source, viewModel.query)
			} else {
				router.openList(item.source, item.listFilter, item.sortOrder)
			}
		}
		val sizeResolver = DynamicItemSizeResolver(resources, this, settings, adjustWidth = true)
		val selectionDecoration = MangaSelectionDecoration(this)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = selectionDecoration,
			registryOwner = this,
			callback = this,
		)
		val adapter = SearchAdapter(
			listener = this,
			itemClickListener = itemClickListener,
			sizeResolver = sizeResolver,
			selectionDecoration = selectionDecoration,
		)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.addItemDecoration(TypedListSpacingDecoration(this, true))

		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setSubtitle(
			if (pickMode) R.string.manga_migration else R.string.search_results,
		)

		addMenuProvider(SearchMenuProvider(this, viewModel))
		if (pickMode) {
			addMenuProvider(PickModeSearchMenuProvider())
		}

		viewModel.list.observe(this, adapter)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.toolbar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		viewBinding.recyclerView.setPadding(
			left = barsInsets.left,
			top = 0,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		if (pickMode) {
			confirmPick(item)
			return
		}
		if (!selectionController.onItemClick(item.id)) {
			router.openDetails(item.toMangaWithOverride())
		}
	}

	private fun confirmPick(item: MangaListModel) {
		val target = item.toMangaWithOverride()
		buildAlertDialog(this, isCentered = true) {
			setTitle(R.string.manga_migration)
			setMessage(
				getString(
					R.string.migrate_to_manga_confirmation,
					target.title,
					target.source.getTitle(context),
				),
			)
			setNegativeButton(android.R.string.cancel, null)
			setNeutralButton(R.string.details) { _, _ ->
				router.openDetails(target)
			}
			setPositiveButton(R.string.migrate) { _, _ ->
				val data = Intent().putExtra(
					AppRouter.KEY_MANGA,
					ParcelableManga(target, withDescription = false),
				)
				setResult(Activity.RESULT_OK, data)
				finish()
			}
		}.show()
	}

	override fun onItemLongClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id)
	}

	override fun onItemContextClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id)
	}

	override fun onReadClick(manga: Manga, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openReader(manga)
		}
	}

	override fun onTagClick(manga: Manga, tag: MangaTag, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openList(tag)
		}
	}

	override fun onRetryClick(error: Throwable) {
		viewModel.retry()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = viewModel.continueSearch()

	override fun onListHeaderClick(item: ListHeader, view: View) = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateNestedItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_share -> {
				ShareHelper(this).shareMangaLinks(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_favourite -> {
				router.showFavoriteDialog(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_save -> {
				router.showDownloadDialog(collectSelectedItems(), viewBinding.recyclerView)
				mode?.finish()
				true
			}

			else -> false
		}
	}

	private fun collectSelectedItems(): Set<Manga> {
		return viewModel.getItems(selectionController.peekCheckedIds())
	}

	private inner class PickModeSearchMenuProvider :
		MenuProvider,
		SearchView.OnQueryTextListener {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_search_pick, menu)
			val menuItem = menu.findItem(R.id.action_search)
			val searchView = menuItem.actionView as SearchView
			searchView.queryHint = getString(R.string.search_manga)
			searchView.setIconifiedByDefault(false)
			searchView.maxWidth = Int.MAX_VALUE
			searchView.setOnQueryTextListener(this)
			searchView.setQuery(viewModel.query, false)
			searchView.clearFocus()
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = false

		override fun onQueryTextSubmit(query: String?): Boolean {
			val q = query?.trim().orEmpty()
			if (q.isNotEmpty()) {
				viewModel.search(q)
				this@SearchActivity.title = q
			}
			return true
		}

		override fun onQueryTextChange(newText: String?): Boolean = false
	}
}
