package com.kosen.reader.explore.ui.model

import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.list.ui.model.MangaCompactListModel

data class RecommendationsItem(
	val manga: List<MangaCompactListModel>
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is RecommendationsItem
	}
}
