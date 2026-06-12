package com.kosen.reader.list.domain

import androidx.collection.ArraySet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.kosen.reader.R
import com.kosen.reader.core.model.toChipModel
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.list.ui.model.QuickFilter
import com.kosen.reader.parsers.util.suspendlazy.getOrNull
import com.kosen.reader.parsers.util.suspendlazy.suspendLazy

abstract class MangaListQuickFilter(
	private val settings: AppSettings,
) : QuickFilterListener {

	private val appliedFilter = MutableStateFlow<Set<ListFilterOption>>(emptySet())
	private val availableFilterOptions = suspendLazy {
		getAvailableFilterOptions()
	}

	val appliedOptions
		get() = appliedFilter.asStateFlow()

	override fun setFilterOption(option: ListFilterOption, isApplied: Boolean) {
		appliedFilter.value = ArraySet(appliedFilter.value).also {
			if (isApplied) {
				it.addNoConflicts(option)
			} else {
				it.remove(option)
			}
		}
	}

	override fun toggleFilterOption(option: ListFilterOption) {
		appliedFilter.value = ArraySet(appliedFilter.value).also {
			if (option in it) {
				it.remove(option)
			} else {
				it.addNoConflicts(option)
			}
		}
	}

	override fun clearFilter() {
		appliedFilter.value = emptySet()
	}

	fun clearPrivateHistoryFilter() {
		setFilterOption(ListFilterOption.Macro.PRIVATE_HISTORY, isApplied = false)
	}

	suspend open fun filterItem(
		selectedOptions: Set<ListFilterOption>,
	): QuickFilter? {
		if (!settings.isQuickFilterEnabled) {
			return null
		}
		val availableOptions = availableFilterOptions.getOrNull()?.map { option ->
			if (option == ListFilterOption.Macro.PRIVATE_HISTORY) {
				ChipsView.ChipModel(
					title = null,
					titleResId = if (option in selectedOptions) {
						R.string.private_history_filter_revealed
					} else {
						R.string.private_history_filter
					},
					icon = 0,
					iconData = null,
					isChecked = option in selectedOptions,
					counter = 0,
					data = option,
				)
			} else {
				option.toChipModel(isChecked = option in selectedOptions)
			}
		}.orEmpty()
		return if (availableOptions.isNotEmpty()) {
			QuickFilter(availableOptions)
		} else {
			null
		}
	}

	protected abstract suspend fun getAvailableFilterOptions(): List<ListFilterOption>

	private fun ArraySet<ListFilterOption>.addNoConflicts(option: ListFilterOption) {
		add(option)
		if (option is ListFilterOption.Inverted) {
			remove(option.option)
		} else {
			removeIf { it is ListFilterOption.Inverted && it.option == option }
		}
	}
}
