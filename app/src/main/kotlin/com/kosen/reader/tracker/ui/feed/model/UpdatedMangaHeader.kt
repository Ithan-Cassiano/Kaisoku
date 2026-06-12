package com.kosen.reader.tracker.ui.feed.model

import com.kosen.reader.list.ui.ListModelDiffCallback
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaListModel

data class UpdatedMangaHeader(
	val list: List<MangaListModel>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is UpdatedMangaHeader
	}

	override fun getChangePayload(previousState: ListModel): Any {
		return ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
	}
}
