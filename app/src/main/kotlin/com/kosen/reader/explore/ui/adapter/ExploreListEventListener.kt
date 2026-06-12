package com.kosen.reader.explore.ui.adapter

import android.view.View
import com.kosen.reader.list.ui.adapter.ListHeaderClickListener
import com.kosen.reader.list.ui.adapter.ListStateHolderListener

interface ExploreListEventListener : ListStateHolderListener, View.OnClickListener, ListHeaderClickListener
