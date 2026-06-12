package com.kosen.reader.reader.ui.pager.doublereversed

import com.kosen.reader.reader.ui.ReaderState
import com.kosen.reader.reader.ui.pager.ReaderPage
import com.kosen.reader.reader.ui.pager.doublepage.DoubleReaderFragment

class ReversedDoubleReaderFragment : DoubleReaderFragment() {

	override fun switchPageBy(delta: Int) {
		super.switchPageBy(-delta)
	}

	override fun switchPageTo(position: Int, smooth: Boolean) {
		super.switchPageTo(reversed(position), smooth)
	}

	override suspend fun onPagesChanged(pages: List<ReaderPage>, pendingState: ReaderState?) {
		super.onPagesChanged(pages.reversed(), pendingState)
	}

	override fun notifyPageChanged(lowerPos: Int, upperPos: Int) {
		val reversedLower = positionMap.getOrElse(upperPos) { -1 }
		val reversedUpper = positionMap.getOrElse(lowerPos) { -1 }
		val totalPages = originalPageCount
		val originalLower = if (reversedLower >= 0) (totalPages - 1 - reversedLower) else -1
		val originalUpper = if (reversedUpper >= 0) (totalPages - 1 - reversedUpper) else -1
		val lower = if (originalLower >= 0) originalLower else originalUpper
		val upper = if (originalUpper >= 0) originalUpper else originalLower
		if (lower >= 0) {
			viewModel.onCurrentPageChanged(lower, upper.coerceAtLeast(lower))
		}
	}

	private fun reversed(position: Int): Int {
		return (originalPageCount - position - 1).coerceAtLeast(0)
	}
}
