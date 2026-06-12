package com.kosen.reader.list.ui.model

import com.kosen.reader.core.ui.model.MangaOverride
import com.kosen.reader.parsers.model.Manga

data class MangaCompactListModel(
	override val manga: Manga,
	override val override: MangaOverride?,
	val subtitle: String,
	override val counter: Int,
) : MangaListModel()
