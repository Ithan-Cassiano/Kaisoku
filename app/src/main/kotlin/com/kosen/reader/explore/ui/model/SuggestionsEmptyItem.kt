package com.kosen.reader.explore.ui.model

import com.kosen.reader.list.ui.model.ListModel

data class SuggestionsEmptyItem(
	val filterHint: String?,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean = other is SuggestionsEmptyItem
}
