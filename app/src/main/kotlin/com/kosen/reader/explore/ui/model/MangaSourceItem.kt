package com.kosen.reader.explore.ui.model

import com.kosen.reader.core.model.MangaSourceInfo
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.util.longHashCode

data class MangaSourceItem(
	val source: MangaSourceInfo,
	val isGrid: Boolean,
) : ListModel {

	val id: Long = source.name.longHashCode()

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is MangaSourceItem && other.source == source
	}
}
