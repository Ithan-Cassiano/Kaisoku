package com.kosen.reader.list.ui.size

import android.view.View
import android.widget.TextView
import com.kosen.reader.history.ui.util.ReadingProgressView

interface ItemSizeResolver {

	val cellWidth: Int

	fun attachToView(
		view: View,
		textView: TextView?,
		progressView: ReadingProgressView?,
	)
}
