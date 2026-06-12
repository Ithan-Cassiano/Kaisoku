package com.kosen.reader.search.ui.suggestion

import android.text.TextWatcher
import android.widget.TextView
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.search.domain.SearchKind

interface SearchSuggestionListener : TextWatcher, TextView.OnEditorActionListener {

	fun onMangaClick(manga: Manga)

	fun onQueryClick(query: String, kind: SearchKind, submit: Boolean)

	fun onSourceToggle(source: MangaSource, isEnabled: Boolean)

	fun onSourceClick(source: MangaSource)

	fun onTagClick(tag: MangaTag)

	fun onRemoveQuery(query: String)
}
