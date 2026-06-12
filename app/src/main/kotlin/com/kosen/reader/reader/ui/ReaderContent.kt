package com.kosen.reader.reader.ui

import com.kosen.reader.reader.ui.pager.ReaderPage

data class ReaderContent(
	val pages: List<ReaderPage>,
	val state: ReaderState?
)