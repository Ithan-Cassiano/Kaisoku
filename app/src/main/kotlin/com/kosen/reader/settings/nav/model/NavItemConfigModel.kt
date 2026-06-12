package com.kosen.reader.settings.nav.model

import androidx.annotation.StringRes
import com.kosen.reader.core.prefs.NavItem
import com.kosen.reader.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
