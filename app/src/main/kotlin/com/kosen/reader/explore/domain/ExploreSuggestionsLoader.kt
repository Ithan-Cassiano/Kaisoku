package com.kosen.reader.explore.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.suggestions.domain.SuggestionRepository
import com.kosen.reader.suggestions.ui.SuggestionsWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExploreSuggestionsLoader @Inject constructor(
	private val suggestionRepository: SuggestionRepository,
	private val exploreRepository: ExploreRepository,
	private val scheduler: SuggestionsWorker.Scheduler,
) {

	fun observePreview(
		count: Int,
		filters: Set<ListFilterOption>,
	): Flow<List<Manga>> = suggestionRepository.observeAll(0, filters).mapLatest { stored ->
		if (stored.isEmpty()) {
			ensureSuggestionsUpdating()
			exploreRepository.getQuickSuggestions(count, filters)
		} else {
			stored.shuffled().take(count)
		}
	}

	suspend fun ensureSuggestionsUpdating() {
		scheduler.schedule()
		if (suggestionRepository.isEmpty()) {
			scheduler.startNow()
		}
	}
}
