package com.kosen.reader.explore.ui.adapter

import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.explore.ui.model.MangaSourceItem
import com.kosen.reader.parsers.model.Manga

class DevExploreAdapter(
	listener: ExploreListEventListener,
	clickListener: OnListItemClickListener<MangaSourceItem>,
	mangaClickListener: OnListItemClickListener<Manga>,
) : ExploreAdapter(listener, clickListener, mangaClickListener)
