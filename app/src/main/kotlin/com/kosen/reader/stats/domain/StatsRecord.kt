package com.kosen.reader.stats.domain

import com.kosen.reader.details.data.ReadingTime
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.parsers.model.Manga
import java.util.concurrent.TimeUnit

data class StatsRecord(
	val manga: Manga?,
	val duration: Long,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is StatsRecord && other.manga == manga
	}

	val time: ReadingTime

	init {
		val minutes = TimeUnit.MILLISECONDS.toMinutes(duration).toInt()
		time = ReadingTime(
			minutes = minutes % 60,
			hours = minutes / 60,
			isContinue = false,
		)
	}
}
