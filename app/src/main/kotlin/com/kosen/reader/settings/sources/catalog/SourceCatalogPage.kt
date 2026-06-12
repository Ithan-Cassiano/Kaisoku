package com.kosen.reader.settings.sources.catalog

import com.kosen.reader.list.ui.ListModelDiffCallback
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.ContentType

data class SourceCatalogPage(
	val type: ContentType,
	val items: List<SourceCatalogItem>,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is SourceCatalogPage && other.type == type
	}

	override fun getChangePayload(previousState: ListModel): Any {
		return ListModelDiffCallback.PAYLOAD_NESTED_LIST_CHANGED
	}
}
