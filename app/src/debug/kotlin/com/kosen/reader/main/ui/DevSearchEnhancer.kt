package com.kosen.reader.main.ui

import android.view.ViewGroup
import androidx.core.view.isVisible
import com.kosen.reader.R
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.databinding.ActivityMainBinding
import com.kosen.reader.search.domain.SearchKind

object DevSearchEnhancer {

	private enum class Scope(val kind: SearchKind, val titleRes: Int) {
		TITLE(SearchKind.TITLE, R.string.dev_search_scope_title),
		SOURCE(SearchKind.SIMPLE, R.string.dev_search_scope_source),
		TAG(SearchKind.TAG, R.string.dev_search_scope_tag),
		AUTHOR(SearchKind.AUTHOR, R.string.dev_search_scope_author),
	}

	fun attach(activity: MainActivity, binding: ActivityMainBinding) {
		if (binding.searchContent.findViewWithTag<ChipsView>(CHIPS_TAG) != null) {
			return
		}
		val chipsView = ChipsView(activity).apply {
			tag = CHIPS_TAG
			layoutParams = ViewGroup.MarginLayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT,
			).apply {
				val margin = resources.getDimensionPixelSize(R.dimen.list_spacing_normal)
				setMargins(margin, margin / 2, margin, margin / 2)
			}
		}
		var selectedScope = Scope.TITLE
		binding.searchContent.addView(chipsView, 0)

		fun bindChips() {
			chipsView.setChips(
				Scope.entries.map { scope ->
					ChipsView.ChipModel(
						title = activity.getString(scope.titleRes),
						isChecked = scope == selectedScope,
						data = scope,
					)
				},
			)
		}
		chipsView.onChipClickListener = ChipsView.OnChipClickListener { _, data ->
			selectedScope = data as Scope
			bindChips()
		}
		bindChips()
		binding.searchView.addTransitionListener { _, _, newState ->
			chipsView.isVisible = newState >= com.google.android.material.search.SearchView.TransitionState.SHOWING
		}
	}

	private const val CHIPS_TAG = "search_scope_chips"
}
