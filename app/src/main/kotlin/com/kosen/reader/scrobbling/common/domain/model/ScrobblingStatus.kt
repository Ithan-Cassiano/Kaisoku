package com.kosen.reader.scrobbling.common.domain.model

import com.kosen.reader.list.ui.model.ListModel

enum class ScrobblingStatus : ListModel {

	PLANNED, READING, RE_READING, COMPLETED, ON_HOLD, DROPPED;

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is ScrobblingStatus && other.ordinal == ordinal
	}
}
