package com.kosen.reader.list.ui.model

import com.kosen.reader.core.ui.model.MangaOverride
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.list.ui.ListModelDiffCallback.Companion.PAYLOAD_ANYTHING_CHANGED
import com.kosen.reader.list.ui.ListModelDiffCallback.Companion.PAYLOAD_PROGRESS_CHANGED
import com.kosen.reader.parsers.model.Manga

data class MangaGridModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	override val counter: Int,
	val progress: ReadingProgress?,
	val isFavorite: Boolean,
	val isSaved: Boolean,
	val isTitleHidden: Boolean = false,
	val isHiddenFromMain: Boolean = false,
	val isCoverBlurred: Boolean = false,
) : MangaListModel() {

	override fun getChangePayload(previousState: ListModel): Any? = when {
		previousState !is MangaGridModel || previousState.manga != manga -> null

		previousState.progress != progress -> PAYLOAD_PROGRESS_CHANGED
		previousState.isFavorite != isFavorite ||
			previousState.isSaved != isSaved ||
			previousState.isTitleHidden != isTitleHidden ||
			previousState.isHiddenFromMain != isHiddenFromMain ||
			previousState.isCoverBlurred != isCoverBlurred -> PAYLOAD_ANYTHING_CHANGED

		else -> super.getChangePayload(previousState)
	}
}
