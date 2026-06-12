package com.kosen.reader.filter.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.kosen.reader.R
import com.kosen.reader.core.model.titleResId
import com.kosen.reader.core.ui.widgets.ChipsView
import com.kosen.reader.filter.data.PersistableFilter
import com.kosen.reader.filter.ui.model.FilterHeaderModel
import com.kosen.reader.filter.ui.model.FilterProperty
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaListFilterCapabilities
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.parsers.util.toTitleCase
import com.kosen.reader.search.domain.MangaSearchRepository
import javax.inject.Inject
import androidx.appcompat.R as appcompatR

class FilterHeaderProducer @Inject constructor(
    private val searchRepository: MangaSearchRepository,
) {

    fun observeHeader(filterCoordinator: FilterCoordinator): Flow<FilterHeaderModel> {
        return combine(
            filterCoordinator.savedFilters,
            filterCoordinator.tags,
            filterCoordinator.observe(),
        ) { saved, tags, snapshot ->
            val chipList = createChipsList(
                source = filterCoordinator.mangaSource,
                capabilities = filterCoordinator.capabilities,
                savedFilters = saved,
                tagsProperty = tags,
                snapshot = snapshot.listFilter,
                limit = 12,
            )
            FilterHeaderModel(
                chips = chipList,
                sortOrder = snapshot.sortOrder,
                isFilterApplied = !snapshot.listFilter.isEmpty(),
            )
        }
    }

    private suspend fun createChipsList(
        source: MangaSource,
        capabilities: MangaListFilterCapabilities,
        savedFilters: FilterProperty<PersistableFilter>,
        tagsProperty: FilterProperty<MangaTag>,
        snapshot: MangaListFilter,
        limit: Int,
    ): List<ChipsView.ChipModel> {
        val result = ArrayDeque<ChipsView.ChipModel>(savedFilters.availableItems.size + limit + 3)
        if (snapshot.query.isNullOrEmpty() || capabilities.isSearchWithFiltersSupported) {
            val selectedTags = tagsProperty.selectedItems.toMutableSet()
            var tags = if (selectedTags.isEmpty()) {
                searchRepository.getTagsSuggestion("", limit, source)
            } else {
                searchRepository.getTagsSuggestion(selectedTags).take(limit)
            }
            if (tags.size < limit) {
                tags = tags + tagsProperty.availableItems.take(limit - tags.size)
            }
            if (tags.isEmpty() && selectedTags.isEmpty()) {
                return emptyList()
            }
            for (saved in savedFilters.availableItems) {
                val model = ChipsView.ChipModel(
                    title = saved.name,
                    isChecked = saved in savedFilters.selectedItems,
                    data = saved,
                )
                if (model.isChecked) {
                    selectedTags.removeAll(saved.filter.tags)
                    result.addFirst(model)
                } else {
                    result.addLast(model)
                }
            }
            for (tag in tags) {
                val model = ChipsView.ChipModel(
                    title = tag.title,
                    isChecked = selectedTags.remove(tag),
                    data = tag,
                )
                if (model.isChecked) {
                    result.addFirst(model)
                } else {
                    result.addLast(model)
                }
            }
            for (tag in selectedTags) {
                val model = ChipsView.ChipModel(
                    title = tag.title,
                    isChecked = true,
                    data = tag,
                )
                result.addFirst(model)
            }
        }
        snapshot.locale?.let {
            result.addFirst(
                ChipsView.ChipModel(
                    title = it.getDisplayName(it).toTitleCase(it),
                    icon = R.drawable.ic_language,
                    isCloseable = true,
                    data = it,
                ),
            )
        }
        snapshot.types.forEach {
            result.addFirst(
                ChipsView.ChipModel(
                    titleResId = it.titleResId,
                    isCloseable = true,
                    data = it,
                ),
            )
        }
        snapshot.demographics.forEach {
            result.addFirst(
                ChipsView.ChipModel(
                    titleResId = it.titleResId,
                    isCloseable = true,
                    data = it,
                ),
            )
        }
        snapshot.contentRating.forEach {
            result.addFirst(
                ChipsView.ChipModel(
                    titleResId = it.titleResId,
                    isCloseable = true,
                    data = it,
                ),
            )
        }
        snapshot.states.forEach {
            result.addFirst(
                ChipsView.ChipModel(
                    titleResId = it.titleResId,
                    isCloseable = true,
                    data = it,
                ),
            )
        }
        if (!snapshot.query.isNullOrEmpty()) {
            result.addFirst(
                ChipsView.ChipModel(
                    title = snapshot.query,
                    icon = appcompatR.drawable.abc_ic_search_api_material,
                    isCloseable = true,
                    data = snapshot.query,
                ),
            )
        }
        if (!snapshot.author.isNullOrEmpty()) {
            result.addFirst(
                ChipsView.ChipModel(
                    title = snapshot.author,
                    icon = R.drawable.ic_user,
                    isCloseable = true,
                    data = snapshot.author,
                ),
            )
        }
        val hasTags = result.any { it.data is MangaTag }
        if (hasTags) {
            result.addFirst(moreTagsChip())
        }
        return result
    }

    private fun moreTagsChip() = ChipsView.ChipModel(
        titleResId = R.string.genres,
        icon = R.drawable.ic_drawer_menu_open,
    )
}
