package com.kosen.reader.suggestions.domain

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.domain.MangaListQuickFilter
import com.kosen.reader.list.ui.model.QuickFilter

@Singleton
class SuggestionsListQuickFilter @Inject constructor(
	private val settings: AppSettings,
	private val suggestionRepository: SuggestionRepository,
	@ApplicationContext private val context: Context,
) : MangaListQuickFilter(settings) {

	override suspend fun getAvailableFilterOptions(): List<ListFilterOption> = buildList {
		addAll(buildSuggestionsNsfwFilterOptions(!settings.isNsfwContentDisabled))
		addAll(buildSuggestionsContentTypeFilterOptions())
		appendSuggestionsTagFilterOptions(this, suggestionRepository.getTopTags(5))
		appendSuggestionsSourceFilterOptions(this, suggestionRepository.getTopSources(3))
	}

	suspend fun getFilterSheetOptions(): List<ListFilterOption> = buildList {
		addAll(buildSuggestionsNsfwFilterOptions(!settings.isNsfwContentDisabled))
		addAll(buildSuggestionsContentTypeFilterOptions())
	}

	override suspend fun filterItem(selectedOptions: Set<ListFilterOption>): QuickFilter? {
		if (!settings.isQuickFilterEnabled) {
			return null
		}
		val availableOptions = getAvailableFilterOptions().map { option ->
			ChipsView.ChipModel(
				title = getSuggestionsFilterChipTitle(context, option),
				icon = option.iconResId,
				iconData = option.getIconData(),
				isChecked = option in selectedOptions,
				counter = 0,
				data = option,
			)
		}
		return if (availableOptions.isNotEmpty()) {
			QuickFilter(availableOptions)
		} else {
			null
		}
	}
}
