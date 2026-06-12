package com.kosen.reader.list.ui.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class EmptyState(
	@DrawableRes val icon: Int,
	@StringRes val textPrimary: Int,
	@StringRes val textSecondary: Int,
	@StringRes val actionStringRes: Int,
	@StringRes val secondaryActionStringRes: Int = 0,
	@StringRes val tertiaryActionStringRes: Int = 0,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is EmptyState
	}
}
