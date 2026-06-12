package com.kosen.reader.scrobbling.common.ui.selector.adapter

import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.ListStateHolderListener
import com.kosen.reader.list.ui.adapter.loadingFooterAD
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel
import com.kosen.reader.scrobbling.common.domain.model.ScrobblerManga

class ScrobblerSelectorAdapter(
	clickListener: OnListItemClickListener<ScrobblerManga>,
	stateHolderListener: ListStateHolderListener,
) : BaseListAdapter<ListModel>() {

	init {
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
		addDelegate(ListItemType.MANGA_SCROBBLING, scrobblingMangaAD(clickListener))
		addDelegate(ListItemType.FOOTER_LOADING, loadingFooterAD())
		addDelegate(ListItemType.HINT_EMPTY, scrobblerHintAD(stateHolderListener))
	}
}
