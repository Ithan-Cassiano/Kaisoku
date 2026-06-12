package com.kosen.reader.explore.domain

import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.runCatchingCancellable
import javax.inject.Inject

class SourceHealthTestUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend fun runTests(source: MangaSource): SourceHealthTestResult {
		val repository = mangaRepositoryFactory.create(source)
		val listOk = runCatchingCancellable {
			repository.getList(0, SortOrder.UPDATED, MangaListFilter.EMPTY).isNotEmpty()
		}.getOrDefault(false)
		if (!listOk) {
			return SourceHealthTestResult(listOk = false, detailsOk = false, pagesOk = false)
		}
		val firstManga = repository.getList(0, SortOrder.UPDATED, MangaListFilter.EMPTY).firstOrNull()
			?: return SourceHealthTestResult(listOk = true, detailsOk = false, pagesOk = false)
		val details = runCatchingCancellable {
			repository.getDetails(firstManga)
		}.getOrNull()
		val detailsOk = details != null && !details.chapters.isNullOrEmpty()
		if (!detailsOk) {
			return SourceHealthTestResult(listOk = true, detailsOk = false, pagesOk = false)
		}
		val chapter = details.chapters?.firstOrNull()
			?: return SourceHealthTestResult(listOk = true, detailsOk = true, pagesOk = false)
		val pagesOk = runCatchingCancellable {
			repository.getPages(chapter).isNotEmpty()
		}.getOrDefault(false)
		SourceHealthTracker.recordSuccess(source)
		return SourceHealthTestResult(listOk = true, detailsOk = true, pagesOk = pagesOk)
	}

	data class SourceHealthTestResult(
		val listOk: Boolean,
		val detailsOk: Boolean,
		val pagesOk: Boolean,
	) {
		val isFullyOk: Boolean
			get() = listOk && detailsOk && pagesOk
	}
}
