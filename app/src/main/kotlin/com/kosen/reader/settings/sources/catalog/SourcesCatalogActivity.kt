package com.kosen.reader.settings.sources.catalog

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.Insets
import androidx.core.view.children
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import com.kosen.reader.R
import com.kosen.reader.core.model.titleResId
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.image.CoilImageView
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.util.FadingAppbarMediator
import com.kosen.reader.core.ui.util.ReversibleActionObserver
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.core.ui.widgets.ChipsView.ChipModel
import com.kosen.reader.core.util.LocaleComparator
import com.kosen.reader.core.util.ext.getDisplayName
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.toLocale
import com.kosen.reader.databinding.ActivitySourcesCatalogBinding
import com.kosen.reader.list.ui.adapter.TypedListSpacingDecoration
import com.kosen.reader.main.ui.owners.AppBarOwner
import com.kosen.reader.parsers.model.ContentType

@AndroidEntryPoint
class SourcesCatalogActivity : BaseActivity<ActivitySourcesCatalogBinding>(),
	OnListItemClickListener<SourceCatalogItem.Source>,
	AppBarOwner,
	MenuItem.OnActionExpandListener,
	ChipsView.OnChipClickListener {

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	private val viewModel by viewModels<SourcesCatalogViewModel>()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySourcesCatalogBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val sourcesAdapter = SourcesCatalogAdapter(this)
		with(viewBinding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(TypedListSpacingDecoration(context, false))
			adapter = sourcesAdapter
		}
		viewBinding.chipsFilter.onChipClickListener = this
		FadingAppbarMediator(viewBinding.appbar, viewBinding.toolbar).bind()
		viewModel.content.observe(this, Lifecycle.State.STARTED, sourcesAdapter)
		viewModel.onActionDone.observeEvent(
			this,
			ReversibleActionObserver(viewBinding.recyclerView),
		)
		combine(viewModel.appliedFilter, viewModel.hasNewSources, viewModel.contentTypes, ::Triple).observe(this, Lifecycle.State.STARTED) {
			updateFilers(it.first, it.second, it.third)
		}
		addMenuProvider(SourcesCatalogMenuProvider(this, viewModel, this))
	}

	override fun onDestroy() {
		if (hasViewBinding()) {
			viewBinding.recyclerView.children.forEach { child ->
				child.findViewById<CoilImageView?>(R.id.imageView_icon)?.disposeImage()
			}
			viewBinding.recyclerView.adapter = null
			viewBinding.chipsFilter.onChipClickListener = null
		}
		super.onDestroy()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.recyclerView.updatePadding(
			left = bars.left,
			right = bars.right,
			bottom = bars.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = bars.left,
			right = bars.right,
			top = bars.top,
		)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ContentType -> {
				val selecting = !chip.isChecked
				viewModel.setContentType(data, selecting)
				if (selecting) {
					viewModel.getPreferredSource(data)?.let { preferred ->
						router.openList(preferred, null, null)
					}
				}
			}
			is Boolean -> viewModel.setNewOnly(!chip.isChecked)
			SourcesCatalogChip.Mihon -> viewModel.cycleMihonMode()
			SourcesCatalogChip.Plugin -> viewModel.cyclePluginMode()
			else -> showLocalesMenu(chip)
		}
	}

	override fun onItemClick(item: SourceCatalogItem.Source, view: View) {
		viewModel.rememberSourceForType(item.source)
		router.openList(item.source, null, null)
	}

	override fun onItemLongClick(item: SourceCatalogItem.Source, view: View): Boolean {
		viewModel.addSource(item.source)
		return false
	}

	override fun onMenuItemActionExpand(item: MenuItem): Boolean {
		val sq = (item.actionView as? SearchView)?.query?.trim()?.toString().orEmpty()
		viewModel.performSearch(sq)
		return true
	}

	override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
		viewModel.performSearch(null)
		return true
	}

	private fun updateFilers(
		appliedFilter: SourcesCatalogFilter,
		hasNewSources: Boolean,
		contentTypes: List<ContentType>,
	) {
		val chips = ArrayList<ChipModel>(contentTypes.size + 3)
		chips += ChipModel(
			title = appliedFilter.locale?.toLocale().getDisplayName(this),
			icon = R.drawable.ic_language,
			isDropdown = true,
		)
		if (hasNewSources) {
			chips += ChipModel(
				title = getString(R.string._new),
				icon = R.drawable.ic_updated,
				isChecked = appliedFilter.isNewOnly,
				data = true,
			)
		}
		chips += ChipModel(
			title = appliedFilter.mihonMode.sourceKindTitle(
				includedTitle = getString(R.string.extensions),
				excludedTitle = getString(R.string.no_extensions),
			),
			icon = appliedFilter.mihonMode.sourceKindIcon(R.drawable.ic_sync),
			isChecked = appliedFilter.mihonMode == SourceCatalogFilterMode.INCLUDE,
			tint = appliedFilter.mihonMode.sourceKindTint(),
			data = SourcesCatalogChip.Mihon,
		)
		chips += ChipModel(
			title = appliedFilter.pluginMode.sourceKindTitle(
				includedTitle = getString(R.string.plugins),
				excludedTitle = getString(R.string.no_plugins),
			),
			icon = appliedFilter.pluginMode.sourceKindIcon(R.drawable.ic_services),
			isChecked = appliedFilter.pluginMode == SourceCatalogFilterMode.INCLUDE,
			tint = appliedFilter.pluginMode.sourceKindTint(),
			data = SourcesCatalogChip.Plugin,
		)
		contentTypes.mapTo(chips) { type ->
			ChipModel(
				title = getString(type.titleResId),
				isChecked = type in appliedFilter.types,
				data = type,
			)
		}
		viewBinding.chipsFilter.setChips(chips)
	}

	private enum class SourcesCatalogChip {
		Mihon,
		Plugin,
	}

	private fun showLocalesMenu(anchor: View) {
		val locales = viewModel.locales.mapTo(ArrayList(viewModel.locales.size)) {
			it to it?.toLocale()
		}
		locales.sortWith(compareBy(nullsFirst(LocaleComparator())) { it.second })
		val menu = PopupMenu(this, anchor)
		for ((i, lc) in locales.withIndex()) {
			menu.menu.add(Menu.NONE, Menu.NONE, i, lc.second.getDisplayName(this))
		}
		menu.setOnMenuItemClickListener {
			viewModel.setLocale(locales.getOrNull(it.order)?.first)
			true
		}
		menu.show()
	}
}

private fun SourceCatalogFilterMode.sourceKindTitle(
	includedTitle: String,
	excludedTitle: String,
): String = if (this == SourceCatalogFilterMode.EXCLUDE) excludedTitle else includedTitle

private fun SourceCatalogFilterMode.sourceKindIcon(defaultIcon: Int): Int = when (this) {
	SourceCatalogFilterMode.EXCLUDE -> R.drawable.ic_off_small
	else -> defaultIcon
}

private fun SourceCatalogFilterMode.sourceKindTint(): Int = when (this) {
	SourceCatalogFilterMode.EXCLUDE -> R.color.error
	else -> 0
}
