package com.kosen.reader.alternatives.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.core.nav.PickMangaContract
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.dialog.buildAlertDialog
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.util.ext.consumeAllSystemBarsInsets
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.systemBarsInsets
import com.kosen.reader.databinding.ActivityAlternativesBinding
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.ListStateHolderListener
import com.kosen.reader.list.ui.adapter.TypedListSpacingDecoration
import com.kosen.reader.list.ui.adapter.buttonFooterAD
import com.kosen.reader.list.ui.adapter.emptyStateListAD
import com.kosen.reader.list.ui.adapter.loadingFooterAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject

@AndroidEntryPoint
class AlternativesActivity : BaseActivity<ActivityAlternativesBinding>(),
	ListStateHolderListener,
	OnListItemClickListener<MangaAlternativeModel> {

	@Inject
	lateinit var coil: ImageLoader

	private val viewModel by viewModels<AlternativesViewModel>()

	private val pickMangaLauncher = registerForActivityResult(PickMangaContract()) { target ->
		if (target != null) {
			confirmMigration(target)
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityAlternativesBinding.inflate(layoutInflater))
		supportActionBar?.run {
			setDisplayHomeAsUpEnabled(true)
			subtitle = viewModel.manga.title
		}
		val listAdapter = BaseListAdapter<ListModel>()
			.addDelegate(ListItemType.MANGA_LIST_DETAILED, alternativeAD(coil, this, this))
			.addDelegate(ListItemType.STATE_EMPTY, emptyStateListAD(null))
			.addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
			.addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
			.addDelegate(ListItemType.FOOTER_BUTTON, buttonFooterAD(this))
		with(viewBinding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, addHorizontalPadding = false))
			adapter = listAdapter
		}

		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
		viewModel.list.observe(this, listAdapter)
		viewModel.onMigrated.observeEvent(this) {
			Toast.makeText(this, R.string.migration_completed, Toast.LENGTH_SHORT).show()
			router.openDetails(it)
			finishAfterTransition()
		}

		addMenuProvider(AlternativesMenuProvider())
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.recyclerView.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			top = barsInsets.top,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: MangaAlternativeModel, view: View) {
		when (view.id) {
			R.id.chip_source -> router.openSearch(item.manga.source, viewModel.manga.title)
			R.id.button_migrate -> confirmMigration(item.manga)
			else -> router.openDetails(item.manga)
		}
	}

	override fun onRetryClick(error: Throwable) = viewModel.retry()

	override fun onEmptyActionClick() = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	private fun confirmMigration(target: Manga) {
		buildAlertDialog(this, isCentered = true) {
			setIcon(R.drawable.ic_replace)
			setTitle(R.string.manga_migration)
			setMessage(
				getString(
					R.string.migrate_confirmation,
					viewModel.manga.title,
					viewModel.manga.source.getTitle(context),
					target.title,
					target.source.getTitle(context),
				),
			)
			setNegativeButton(android.R.string.cancel, null)
			setPositiveButton(R.string.migrate) { _, _ ->
				viewModel.migrate(target)
			}
		}.show()
	}

	private inner class AlternativesMenuProvider : MenuProvider {
		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_alternatives, menu)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_pick_migration -> {
				pickMangaLauncher.launch(viewModel.manga.title)
				true
			}

			else -> false
		}
	}
}
