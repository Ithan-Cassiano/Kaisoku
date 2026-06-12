package com.kosen.reader.reader.ui

import com.google.android.material.slider.LabelFormatter
import com.kosen.reader.parsers.util.format

class PageLabelFormatter : LabelFormatter {

	override fun getFormattedValue(value: Float): String {
		return (value + 1).format(0)
	}
}
