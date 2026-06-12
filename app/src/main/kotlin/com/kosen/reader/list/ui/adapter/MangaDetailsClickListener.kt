package com.kosen.reader.list.ui.adapter

import android.view.View
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaTag

interface MangaDetailsClickListener : OnListItemClickListener<MangaListModel> {

	fun onReadClick(manga: Manga, view: View)

	fun onTagClick(manga: Manga, tag: MangaTag, view: View)
}
