package com.kosen.reader.details.ui.scrobbling

import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.ui.BaseListAdapter
import com.kosen.reader.list.ui.model.ListModel

class ScrollingInfoAdapter(
	router: AppRouter,
) : BaseListAdapter<ListModel>() {

	init {
		delegatesManager.addDelegate(scrobblingInfoAD(router))
	}
}
