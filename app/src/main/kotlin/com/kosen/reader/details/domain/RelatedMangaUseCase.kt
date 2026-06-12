package com.kosen.reader.details.domain

import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.util.runCatchingCancellable
import javax.inject.Inject

class RelatedMangaUseCase @Inject constructor(
	private val mangaRepositoryFactory: MangaRepository.Factory,
) {

	suspend operator fun invoke(seed: Manga) = runCatchingCancellable {
		mangaRepositoryFactory.create(seed.source).getRelated(seed)
	}.onFailure {
		it.printStackTraceDebug()
	}.getOrNull()
}
