package com.kosen.reader.search.ui.multi

import android.content.Context
import androidx.annotation.StringRes
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.list.ui.ListModelDiffCallback
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.SortOrder

data class SearchResultsListModel(
	@StringRes val titleResId: Int,
	val source: MangaSource,
	val listFilter: MangaListFilter?,
	val sortOrder: SortOrder?,
	val list: List<MangaListModel>,
	val error: Throwable?,
) : ListModel {

	fun getTitle(context: Context): String = if (titleResId != 0) {
		context.getString(titleResId)
	} else {
		source.getTitle(context)
	}

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is SearchResultsListModel && source == other.source && titleResId == other.titleResId
	}

	override fun getChangePayload(previousState: ListModel): Any? {
		return if (previousState is SearchResultsListModel && previousState.list != list) {
			ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
		} else {
			super.getChangePayload(previousState)
		}
	}
}
