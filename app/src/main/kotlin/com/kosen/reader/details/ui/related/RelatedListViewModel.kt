package com.kosen.reader.details.ui.related

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus
import com.kosen.reader.R
import com.kosen.reader.core.model.parcelable.ParcelableManga
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.require
import com.kosen.reader.list.domain.MangaListMapper
import com.kosen.reader.list.ui.MangaListViewModel
import com.kosen.reader.list.ui.model.EmptyState
import com.kosen.reader.list.ui.model.LoadingState
import com.kosen.reader.list.ui.model.toErrorState
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.domain.model.LocalManga
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject

@HiltViewModel
class RelatedListViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	settings: AppSettings,
	private val mangaListMapper: MangaListMapper,
	mangaDataRepository: MangaDataRepository,
	@LocalStorageChanges localStorageChanges: SharedFlow<LocalManga?>,
) : MangaListViewModel(settings, mangaDataRepository, localStorageChanges) {

	private val seed = savedStateHandle.require<ParcelableManga>(AppRouter.KEY_MANGA).manga
	private val repository = mangaRepositoryFactory.create(seed.source)
	private val mangaList = MutableStateFlow<List<Manga>?>(null)
	private val listError = MutableStateFlow<Throwable?>(null)
	private var loadingJob: Job? = null

	override val content = combine(
		mangaList,
		observeListModeWithTriggers(),
		listError,
	) { list, mode, error ->
		when {
			list.isNullOrEmpty() && error != null -> listOf(error.toErrorState(canRetry = true))
			list == null -> listOf(LoadingState)
			list.isEmpty() -> listOf(createEmptyState())
			else -> mangaListMapper.toListModelList(list, mode)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState))

	init {
		loadList()
	}

	override fun onRefresh() {
		loadList()
	}

	override fun onRetry() {
		loadList()
	}

	private fun loadList(): Job {
		loadingJob?.let {
			if (it.isActive) return it
		}
		return launchLoadingJob(Dispatchers.Default) {
			try {
				listError.value = null
				mangaList.value = repository.getRelated(seed)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
				listError.value = e
				if (!mangaList.value.isNullOrEmpty()) {
					errorEvent.call(e)
				}
			}
		}.also { loadingJob = it }
	}

	private fun createEmptyState() = EmptyState(
		icon = R.drawable.ic_empty_common,
		textPrimary = R.string.nothing_found,
		textSecondary = 0,
		actionStringRes = 0,
	)
}

