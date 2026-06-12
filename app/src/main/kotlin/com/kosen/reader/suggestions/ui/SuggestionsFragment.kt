package com.kosen.reader.suggestions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.list.ListSelectionController
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.core.util.ext.addMenuProvider
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.databinding.FragmentListBinding
import com.kosen.reader.databinding.FragmentSuggestionsHomeBinding
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.MangaListFragment
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.main.ui.MainViewModel
import com.kosen.reader.suggestions.domain.getSuggestionsFilterChipTitle

@AndroidEntryPoint
open class SuggestionsFragment : MangaListFragment() {

	override val viewModel by viewModels<SuggestionsViewModel>()
	override val isSwipeRefreshEnabled = false

	private var homeBinding: FragmentSuggestionsHomeBinding? = null
	private val mainViewModel by viewModels<MainViewModel>(ownerProducer = { requireActivity() })

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): FragmentListBinding {
		val home = FragmentSuggestionsHomeBinding.inflate(inflater, container, false)
		homeBinding = home
		return home.listContainer
	}

	override fun onCreateFragmentRootView(binding: FragmentListBinding): View {
		return homeBinding?.root ?: binding.root
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val home = homeBinding ?: return
		home.buttonContinue.setOnClickListener { mainViewModel.openLastReader() }
		home.chipsActiveFilters.onChipClickListener = ChipsView.OnChipClickListener { _, data ->
			(data as? ListFilterOption)?.let { viewModel.toggleFilterOption(it) }
		}
		viewModel.appliedFilters.observe(viewLifecycleOwner, Lifecycle.State.STARTED, ::bindActiveFilterChips)
		viewModel.content.observe(viewLifecycleOwner, Lifecycle.State.STARTED) { content ->
			val count = content.count { it is MangaListModel }
			home.buttonContinue.isVisible = count > 0 || viewModel.appliedFilters.value.isNotEmpty()
		}
		addMenuProvider(SuggestionMenuProvider())
		if (settings.isTipEnabled(TIP_SUGGESTIONS)) {
			showSuggestionsOnboarding()
		}
	}

	override fun onDestroyView() {
		homeBinding = null
		super.onDestroyView()
	}

	override fun onScrolledToEnd() = Unit

	override fun onEmptyActionClick() {
		if (viewModel.hasActiveFilters()) {
			viewModel.clearFilter()
		} else {
			router.openSuggestionsSettings()
		}
	}

	override fun onListHeaderFilterClick(item: ListHeader, view: View) {
		router.showSuggestionsFilterSheet()
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

	private fun showSuggestionsOnboarding() {
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.onboarding_suggestions_title)
			.setMessage(R.string.onboarding_suggestions_message)
			.setPositiveButton(android.R.string.ok) { _, _ -> settings.closeTip(TIP_SUGGESTIONS) }
			.setNegativeButton(R.string.skip) { _, _ -> settings.closeTip(TIP_SUGGESTIONS) }
			.show()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu,
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	private inner class SuggestionMenuProvider : androidx.core.view.MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menuInflater.inflate(R.menu.opt_suggestions, menu)
		}

		override fun onPrepareMenu(menu: Menu) {
			super.onPrepareMenu(menu)
			menu.findItem(R.id.action_settings_suggestions)?.isVisible =
				menu.findItem(R.id.action_settings) == null
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
			R.id.action_update -> {
				viewModel.updateSuggestions()
				Snackbar.make(
					requireViewBinding().recyclerView,
					R.string.suggestions_updating,
					Snackbar.LENGTH_LONG,
				).show()
				true
			}

			R.id.action_settings_suggestions -> {
				router.openSuggestionsSettings()
				true
			}

			else -> false
		}
	}

	private companion object {
		const val TIP_SUGGESTIONS = "suggestions_tour"
	}
}
