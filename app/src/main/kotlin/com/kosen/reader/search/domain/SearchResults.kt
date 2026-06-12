package com.kosen.reader.search.domain

import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.SortOrder

data class SearchResults(
	val listFilter: MangaListFilter,
	val sortOrder: SortOrder,
	val manga: List<Manga>,
)
