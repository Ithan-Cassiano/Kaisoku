package com.kosen.reader.list.ui.adapter

interface ListStateHolderListener {

	fun onRetryClick(error: Throwable)

	fun onSecondaryErrorActionClick(error: Throwable) = Unit

	fun onEmptyActionClick()

	fun onEmptySecondaryActionClick() = Unit

	fun onEmptyTertiaryActionClick() = Unit

	fun onFooterButtonClick() = Unit
}
