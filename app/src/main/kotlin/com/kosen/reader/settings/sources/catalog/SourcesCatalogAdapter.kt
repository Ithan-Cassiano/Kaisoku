package com.kosen.reader.settings.sources.catalog

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.hannesdorfmann.adapterdelegates4.AdapterDelegate
import com.hannesdorfmann.adapterdelegates4.ListDelegationAdapter
import kotlinx.coroutines.flow.FlowCollector
import com.kosen.reader.R
import com.kosen.reader.core.image.CoilImageView
import com.kosen.reader.core.model.getTitle
import com.kosen.reader.core.ui.list.OnListItemClickListener
import com.kosen.reader.core.ui.list.fastscroll.FastScroller
import com.kosen.reader.list.ui.adapter.ListItemType
import com.kosen.reader.list.ui.adapter.loadingStateAD
import com.kosen.reader.list.ui.model.ListModel
import java.lang.ref.WeakReference

class SourcesCatalogAdapter(
	listener: OnListItemClickListener<SourceCatalogItem.Source>,
) : ListDelegationAdapter<List<ListModel>>(),
	FastScroller.SectionIndexer,
	FlowCollector<List<ListModel>?> {

	private val weakListener = WeakClickListener(listener)

	init {
		addDelegate(ListItemType.CHAPTER_LIST, sourceCatalogItemSourceAD(weakListener))
		addDelegate(ListItemType.HINT_EMPTY, sourceCatalogItemHintAD())
		addDelegate(ListItemType.STATE_LOADING, loadingStateAD())
	}

	@SuppressLint("NotifyDataSetChanged")
	override suspend fun emit(value: List<ListModel>?) {
		items = value.orEmpty()
		notifyDataSetChanged()
	}

	override fun getSectionText(context: Context, position: Int): CharSequence? {
		return (items?.getOrNull(position) as? SourceCatalogItem.Source)?.source?.getTitle(context)?.take(1)
	}

	override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
		holder.itemView.findViewById<CoilImageView?>(R.id.imageView_icon)?.disposeImage()
		super.onViewRecycled(holder)
	}

	private fun addDelegate(type: ListItemType, delegate: AdapterDelegate<List<ListModel>>) {
		delegatesManager.addDelegate(type.ordinal, delegate)
	}

	private class WeakClickListener(
		listener: OnListItemClickListener<SourceCatalogItem.Source>,
	) : OnListItemClickListener<SourceCatalogItem.Source> {

		private val listenerRef = WeakReference(listener)

		override fun onItemClick(item: SourceCatalogItem.Source, view: View) {
			listenerRef.get()?.onItemClick(item, view)
		}

		override fun onItemLongClick(item: SourceCatalogItem.Source, view: View): Boolean {
			return listenerRef.get()?.onItemLongClick(item, view) ?: false
		}
	}
}
