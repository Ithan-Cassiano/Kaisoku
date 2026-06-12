package com.kosen.reader.suggestions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.kosen.reader.R
import com.kosen.reader.core.ui.sheet.BaseAdaptiveSheet
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.core.util.ext.consume
import com.kosen.reader.databinding.SheetSuggestionsFilterBinding
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.suggestions.domain.SuggestionsListQuickFilter
import com.kosen.reader.suggestions.domain.getSuggestionsFilterChipTitle
import com.kosen.reader.suggestions.domain.isSuggestionsExcludeOption

@AndroidEntryPoint
class SuggestionsFilterSheet : BaseAdaptiveSheet<SheetSuggestionsFilterBinding>(), ChipsView.OnChipClickListener,
	View.OnClickListener {

	@Inject
	lateinit var quickFilter: SuggestionsListQuickFilter

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetSuggestionsFilterBinding {
		return SheetSuggestionsFilterBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetSuggestionsFilterBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.chipsNsfw.onChipClickListener = this
		binding.chipsContentType.onChipClickListener = this
		binding.buttonClear.setOnClickListener(this)
		viewLifecycleOwner.lifecycleScope.launch {
			bindFilters(quickFilter.appliedOptions.value)
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ListFilterOption -> quickFilter.toggleFilterOption(data)
		}
		viewLifecycleOwner.lifecycleScope.launch {
			bindFilters(quickFilter.appliedOptions.value)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_clear -> {
				quickFilter.clearFilter()
				viewLifecycleOwner.lifecycleScope.launch {
					bindFilters(emptySet())
				}
			}
		}
	}

	private suspend fun bindFilters(selected: Set<ListFilterOption>) {
		val binding = viewBinding ?: return
		val options = quickFilter.getFilterSheetOptions()
		val nsfwOptions = options.filter {
			it == ListFilterOption.Macro.NSFW || it == ListFilterOption.SFW
		}
		val contentTypeOptions = options.filter {
			it is ListFilterOption.ContentType || isSuggestionsExcludeOption(it)
		}
		binding.chipsNsfw.isVisible = nsfwOptions.isNotEmpty()
		binding.chipsNsfw.setChips(
			nsfwOptions.map { option ->
				ChipsView.ChipModel(
					title = getSuggestionsFilterChipTitle(requireContext(), option),
					isChecked = option in selected,
					data = option,
				)
			},
		)
		binding.chipsContentType.setChips(
			contentTypeOptions.map { option ->
				ChipsView.ChipModel(
					title = getSuggestionsFilterChipTitle(requireContext(), option),
					isChecked = option in selected,
					data = option,
				)
			},
		)
	}
}
