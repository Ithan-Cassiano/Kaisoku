package com.kosen.reader.explore.ui

import androidx.collection.LongSet
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.plus
import com.kosen.reader.R
import com.kosen.reader.core.model.MangaSourceInfo
import com.kosen.reader.core.os.AppShortcutManager
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.observeAsFlow
import com.kosen.reader.core.prefs.observeAsStateFlow
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.ui.util.ReversibleAction
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.core.util.ext.combine
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.explore.domain.ExploreRepository
import com.kosen.reader.explore.domain.ExploreSuggestionsLoader
import com.kosen.reader.explore.domain.SourcePreferenceByTypeRepository
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.core.model.PluginMangaSource
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.explore.ui.model.ExploreButtons
import com.kosen.reader.explore.ui.model.MangaSourceItem
import com.kosen.reader.explore.ui.model.RecommendationsItem
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.model.EmptyHint
import com.kosen.reader.list.ui.model.ListHeader
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.list.ui.model.MangaCompactListModel
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.suggestions.domain.SuggestionsListQuickFilter
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
	private val settings: AppSettings,
	private val suggestionsLoader: ExploreSuggestionsLoader,
	private val suggestionsQuickFilter: SuggestionsListQuickFilter,
	private val exploreRepository: ExploreRepository,
	private val sourcesRepository: MangaSourcesRepository,
	private val sourcePreferenceByType: SourcePreferenceByTypeRepository,
	private val shortcutManager: AppShortcutManager,
) : BaseViewModel() {

	fun rememberSourceForType(source: MangaSource) {
		val contentType = when (source) {
			is MangaParserSource -> source.contentType
			is PluginMangaSource -> source.contentType
			else -> return
		}
		sourcePreferenceByType.rememberLastSourceForType(contentType, source)
	}

	val isGrid = settings.observeAsStateFlow(
		key = AppSettings.KEY_SOURCES_GRID,
		scope = viewModelScope + Dispatchers.IO,
		valueProducer = { isSourcesGridMode },
	)

	val isAllSourcesEnabled = settings.observeAsStateFlow(
		scope = viewModelScope + Dispatchers.IO,
		key = AppSettings.KEY_SOURCES_ENABLED_ALL,
		valueProducer = { isAllSourcesEnabled },
	)

	private val isSuggestionsEnabled = settings.observeAsFlow(
		key = AppSettings.KEY_SUGGESTIONS,
		valueProducer = { isSuggestionsEnabled },
	)

	val onOpenManga = MutableEventFlow<Manga>()
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val onShowSuggestionsTip = MutableEventFlow<Unit>()
	private val isRandomLoading = MutableStateFlow(false)
	private val mutableContent = MutableStateFlow(buildExploreLoadingStateList(isRandomLoading.value))
	private var contentJob: Job? = null

	val content: StateFlow<List<ListModel>> = mutableContent

	init {
		if (shouldShowSuggestionsTip(settings)) {
			onShowSuggestionsTip.call(Unit)
		}
		val loading = isLoading
		val contentFlow = createContentFlow()
		val randomLoading = isRandomLoading
		val contentState = mutableContent
		contentJob = launchJob(Dispatchers.Default) {
			if (settings.isSuggestionsEnabled) {
				suggestionsLoader.ensureSuggestionsUpdating()
			}
			collectExploreContent(loading, contentFlow, randomLoading, contentState)
		}
	}

	override fun onCleared() {
		contentJob?.cancel()
		contentJob = null
		super.onCleared()
	}

	fun openRandom() {
		if (isRandomLoading.value) {
			return
		}
		launchJob(Dispatchers.Default) {
			isRandomLoading.value = true
			try {
				val manga = exploreRepository.findRandomManga(tagsLimit = 8)
				onOpenManga.call(manga)
			} finally {
				isRandomLoading.value = false
			}
		}
	}

	fun disableSources(sources: Collection<MangaSource>) {
		launchJob(Dispatchers.Default) {
			val rollback = sourcesRepository.setSourcesEnabled(sources, isEnabled = false)
			val message = if (sources.size == 1) R.string.source_disabled else R.string.sources_disabled
			onActionDone.call(ReversibleAction(message, rollback))
		}
	}

	fun requestPinShortcut(source: MangaSource) {
		launchLoadingJob(Dispatchers.Default) {
			shortcutManager.requestPinShortcut(source)
		}
	}

	fun setSourcesPinned(sources: Collection<MangaSource>, isPinned: Boolean) {
		launchJob(Dispatchers.Default) {
			sourcesRepository.setIsPinned(sources, isPinned)
			val message = if (sources.size == 1) {
				if (isPinned) R.string.source_pinned else R.string.source_unpinned
			} else {
				if (isPinned) R.string.sources_pinned else R.string.sources_unpinned
			}
			onActionDone.call(ReversibleAction(message, null))
		}
	}

	fun respondSuggestionTip(isAccepted: Boolean) {
		settings.isSuggestionsEnabled = isAccepted
		settings.closeTip(TIP_SUGGESTIONS)
	}

	fun sourcesSnapshot(ids: LongSet): List<MangaSourceInfo> {
		return content.value.mapNotNull {
			(it as? MangaSourceItem)?.takeIf { x -> x.id in ids }?.source
		}
	}

	private fun createContentFlow(): Flow<List<ListModel>> {
		return createExploreContentFlow(
			enabledSources = sourcesRepository.observeEnabledSources(),
			isSuggestionsEnabled = isSuggestionsEnabled,
			suggestionsLoader = suggestionsLoader,
			suggestionsQuickFilter = suggestionsQuickFilter,
			skipNsfw = settings.observeAsFlow(AppSettings.KEY_DISABLE_NSFW) { isNsfwContentDisabled },
			suggestionsCount = SUGGESTIONS_COUNT,
			isGrid = isGrid,
			isRandomLoading = isRandomLoading,
			isAllSourcesEnabled = isAllSourcesEnabled,
			hasNewSources = sourcesRepository.observeHasNewSourcesForBadge(),
			onError = errorEvent,
		)
	}

	companion object {

		private const val TIP_SUGGESTIONS = "suggestions"
		private const val SUGGESTIONS_COUNT = 8
	}
}

private data class SuggestionsPreview(
	val enabled: Boolean,
	val manga: List<Manga>,
)

private fun buildExploreContentList(
	sources: List<MangaSourceInfo>,
	suggestions: SuggestionsPreview,
	isGrid: Boolean,
	randomLoading: Boolean,
	allSourcesEnabled: Boolean,
	hasNewSources: Boolean,
): List<ListModel> {
	val result = ArrayList<ListModel>(sources.size + 3)
	result += ExploreButtons(randomLoading)
	if (suggestions.enabled) {
		result += ListHeader(
			textRes = R.string.suggestions,
			buttonTextRes = R.string.more,
			filterButtonTextRes = R.string.filter,
			payload = R.id.nav_suggestions,
		)
		if (suggestions.manga.isNotEmpty()) {
			result += RecommendationsItem(suggestions.manga.toRecommendationList())
		}
	}
	if (sources.isNotEmpty()) {
		result += ListHeader(
			textRes = R.string.remote_sources,
			buttonTextRes = if (allSourcesEnabled) R.string.manage else R.string.catalog,
			badge = if (!allSourcesEnabled && hasNewSources) "" else null,
		)
		sources.mapTo(result) { MangaSourceItem(it, isGrid) }
	} else {
		result += EmptyHint(
			icon = R.drawable.ic_empty_common,
			textPrimary = R.string.no_manga_sources,
			textSecondary = R.string.no_manga_sources_text,
			actionStringRes = R.string.catalog,
		)
	}
	return result
}

private fun buildExploreLoadingStateList(randomLoading: Boolean) = listOf(
	ExploreButtons(randomLoading),
	LoadingState,
)

private fun shouldShowSuggestionsTip(settings: AppSettings): Boolean {
	return !settings.isSuggestionsEnabled && settings.isTipEnabled("suggestions")
}

private fun createExploreContentFlow(
	enabledSources: Flow<List<MangaSourceInfo>>,
	isSuggestionsEnabled: Flow<Boolean>,
	suggestionsLoader: ExploreSuggestionsLoader,
	suggestionsQuickFilter: SuggestionsListQuickFilter,
	skipNsfw: Flow<Boolean>,
	suggestionsCount: Int,
	isGrid: Flow<Boolean>,
	isRandomLoading: Flow<Boolean>,
	isAllSourcesEnabled: Flow<Boolean>,
	hasNewSources: Flow<Boolean>,
	onError: MutableEventFlow<Throwable>,
): Flow<List<ListModel>> = combine(
	enabledSources,
	observeExploreSuggestions(
		isSuggestionsEnabled,
		suggestionsLoader,
		suggestionsQuickFilter,
		skipNsfw,
		suggestionsCount,
	),
	isGrid,
	isRandomLoading,
	isAllSourcesEnabled,
	hasNewSources,
	::buildExploreContentList,
).catch { error ->
	error.printStackTraceDebug()
	onError.call(error)
}

private fun observeExploreSuggestions(
	isSuggestionsEnabled: Flow<Boolean>,
	suggestionsLoader: ExploreSuggestionsLoader,
	suggestionsQuickFilter: SuggestionsListQuickFilter,
	skipNsfw: Flow<Boolean>,
	suggestionsCount: Int,
): Flow<SuggestionsPreview> = combine(
	isSuggestionsEnabled,
	suggestionsQuickFilter.appliedOptions,
	skipNsfw,
) { isEnabled, filters, skipNsfwEnabled ->
	Triple(isEnabled, filters, skipNsfwEnabled)
}.flatMapLatest { (isEnabled, filters, skipNsfwEnabled) ->
	if (!isEnabled) {
		flowOf(SuggestionsPreview(enabled = false, manga = emptyList()))
	} else {
		val effectiveFilters = if (skipNsfwEnabled && ListFilterOption.Macro.NSFW !in filters) {
			filters + ListFilterOption.SFW
		} else {
			filters
		}
		suggestionsLoader.observePreview(suggestionsCount, effectiveFilters).map { manga ->
			SuggestionsPreview(enabled = true, manga = manga)
		}
	}
}

private suspend fun collectExploreContent(
	isLoading: Flow<Boolean>,
	content: Flow<List<ListModel>>,
	isRandomLoading: Flow<Boolean>,
	target: MutableStateFlow<List<ListModel>>,
) {
	combine(isLoading, content, isRandomLoading, ::mergeExploreContent).collect {
		target.value = it
	}
}

private fun mergeExploreContent(
	loading: Boolean,
	content: List<ListModel>,
	randomLoading: Boolean,
): List<ListModel> = if (loading) {
	buildExploreLoadingStateList(randomLoading)
} else {
	content
}

private fun List<Manga>.toRecommendationList() = map { manga ->
	MangaCompactListModel(
		manga = manga,
		override = null,
		subtitle = manga.tags.joinToString { it.title },
		counter = 0,
	)
}
