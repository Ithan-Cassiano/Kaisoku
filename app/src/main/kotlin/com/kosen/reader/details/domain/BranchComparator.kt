package com.kosen.reader.details.domain

import com.kosen.reader.core.util.LocaleStringComparator
import com.kosen.reader.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
