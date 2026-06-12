package com.kosen.reader.history.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.kosen.reader.history.domain.PrivateHistoryLockManager
import com.kosen.reader.history.domain.PrivateHistoryNavigationSuppression
import com.kosen.reader.R
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.core.ui.dialog.buildAlertDialog
import com.kosen.reader.core.ui.list.ListSelectionController
import com.kosen.reader.core.ui.list.RecyclerScrollKeeper
import com.kosen.reader.core.ui.util.MenuInvalidator
import com.kosen.reader.core.util.ext.addMenuProvider
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.databinding.FragmentListBinding
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.list.ui.MangaListFragment
import com.kosen.reader.list.ui.model.MangaListModel
import com.kosen.reader.list.ui.size.DynamicItemSizeResolver
import com.kosen.reader.parsers.model.Manga

@AndroidEntryPoint
open class HistoryListFragment : MangaListFragment() {

	@Inject lateinit var privateHistoryLockManager: PrivateHistoryLockManager

	override val viewModel by viewModels<HistoryListViewModel>()
	override val isSwipeRefreshEnabled = true

	private val privateHistoryLockSession = "history_private_filter"

	private var pendingUnlockSuccess: (() -> Unit)? = null
	private var pendingUnlockFailure: (() -> Unit)? = null
	private var isUnlockScreenVisible = false

	private val privateHistoryBackCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			exitPrivateHistoryView()
		}
	}

	private val unlockLauncher = registerForActivityResult(
		ActivityResultContracts.StartActivityForResult(),
	) { result ->
		isUnlockScreenVisible = false
		val onSuccess = pendingUnlockSuccess
		val onFailure = pendingUnlockFailure
		pendingUnlockSuccess = null
		pendingUnlockFailure = null
		if (result.resultCode == Activity.RESULT_OK) {
			onSuccess?.invoke()
		} else {
			onFailure?.invoke()
		}
	}

	override fun onViewBindingCreated(binding: FragmentListBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		RecyclerScrollKeeper(binding.recyclerView).attach()
		addMenuProvider(
			HistoryListMenuProvider(
				binding.root.context,
				router,
				viewModel,
			),
		)
		viewModel.isStatsEnabled.observe(viewLifecycleOwner, MenuInvalidator(requireActivity()))
		viewModel.isPrivateHistoryFilterActive.observe(viewLifecycleOwner) {
			privateHistoryBackCallback.isEnabled = it
			refreshPrivateHistoryContentVisibility()
			onPrivateHistoryFilterActiveChanged(it)
		}
		requireActivity().onBackPressedDispatcher.addCallback(
			viewLifecycleOwner,
			privateHistoryBackCallback,
		)
	}

	protected open fun onPrivateHistoryFilterActiveChanged(isActive: Boolean) = Unit

	override fun onResume() {
		PrivateHistoryNavigationSuppression.end()
		super.onResume()
		ensurePrivateHistoryLockedOnResume()
	}

	override fun onPause() {
		if (shouldExitPrivateHistoryOnPause()) {
			exitPrivateHistoryView()
		} else if (shouldLockPrivateHistoryNow()) {
			privateHistoryLockManager.lock(privateHistoryLockSession)
			refreshPrivateHistoryContentVisibility()
		}
		super.onPause()
	}

	override fun onStop() {
		if (shouldLockPrivateHistoryNow()) {
			privateHistoryLockManager.lock(privateHistoryLockSession)
		}
		super.onStop()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		beginPrivateHistoryNavigationIfNeeded()
		super.onItemClick(item, view)
	}

	override fun onReadClick(manga: Manga, view: View) {
		beginPrivateHistoryNavigationIfNeeded()
		super.onReadClick(manga, view)
	}

	override fun onScrolledToEnd() = viewModel.requestMoreItems()

	override fun onEmptyActionClick() {
		if (viewModel.hasActiveFilters()) {
			viewModel.clearFilter()
		} else {
			router.openSourcesCatalog()
		}
	}

	override fun onFilterOptionClick(option: ListFilterOption) {
		if (option == ListFilterOption.Macro.PRIVATE_HISTORY) {
			return
		}
		super.onFilterOptionClick(option)
	}

	fun openPrivateHistoryViaShortcut() {
		if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
			return
		}
		if (viewModel.isPrivateHistoryFilterActive.value) {
			exitPrivateHistoryView()
			return
		}
		privateHistoryLockManager.lock(privateHistoryLockSession)
		requestPrivateHistoryUnlock(
			onSuccess = {
				viewModel.toggleFilterOption(ListFilterOption.Macro.PRIVATE_HISTORY)
			},
		)
	}

	fun exitPrivateHistoryView() {
		if (!viewModel.isPrivateHistoryFilterActive.value) {
			return
		}
		privateHistoryLockManager.lock(privateHistoryLockSession)
		viewModel.clearPrivateHistoryFilter()
		if (view != null) {
			updatePrivateHistoryContentHidden(isLocked = false)
		}
	}

	private fun shouldExitPrivateHistoryOnPause(): Boolean =
		viewModel.isPrivateHistoryFilterActive.value &&
			!isUnlockScreenVisible &&
			!PrivateHistoryNavigationSuppression.isActive &&
			requireActivity().isFinishing

	private fun shouldLockPrivateHistoryNow(): Boolean =
		viewModel.isPrivateHistoryFilterActive.value &&
			!isUnlockScreenVisible &&
			!PrivateHistoryNavigationSuppression.isActive

	private fun beginPrivateHistoryNavigationIfNeeded() {
		if (viewModel.isPrivateHistoryFilterActive.value) {
			PrivateHistoryNavigationSuppression.begin()
		}
	}

	private fun ensurePrivateHistoryLockedOnResume() {
		if (!viewModel.isPrivateHistoryFilterActive.value) {
			return
		}
		if (isUnlockScreenVisible) {
			return
		}
		requestPrivateHistoryUnlock(
			onSuccess = { refreshPrivateHistoryContentVisibility() },
			onFailure = { viewModel.clearPrivateHistoryFilter() },
		)
	}

	private fun requestPrivateHistoryUnlock(
		onSuccess: () -> Unit,
		onFailure: () -> Unit = {},
	) {
		if (!privateHistoryLockManager.isLockEnabled) {
			onSuccess()
			return
		}
		if (privateHistoryLockManager.isUnlocked(privateHistoryLockSession)) {
			onSuccess()
			return
		}
		updatePrivateHistoryContentHidden(isLocked = true)
		pendingUnlockSuccess = onSuccess
		pendingUnlockFailure = onFailure
		isUnlockScreenVisible = true
		unlockLauncher.launch(
			PrivateHistoryUnlockActivity.newUnlockIntent(
				requireContext(),
				privateHistoryLockSession,
			),
		)
	}

	private fun refreshPrivateHistoryContentVisibility() {
		val active = viewModel.isPrivateHistoryFilterActive.value
		val isLocked = active &&
			privateHistoryLockManager.isLockEnabled &&
			!privateHistoryLockManager.isUnlocked(privateHistoryLockSession)
		updatePrivateHistoryContentHidden(isLocked)
	}

	private fun updatePrivateHistoryContentHidden(isLocked: Boolean) {
		requireViewBinding().recyclerView.isVisible = !isLocked
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_history, menu)
		return super.onCreateActionMode(controller, menuInflater, menu)
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		menu.findItem(R.id.action_hide_from_main)?.isVisible =
			viewModel.isHiddenHistoryModeEnabled() &&
				selectedItems.any { viewModel.canHideFromMainHistory(it) }
		menu.findItem(R.id.action_show_in_main)?.isVisible =
			viewModel.isHiddenHistoryModeEnabled() &&
				selectedItems.any { viewModel.canShowInMainHistory(it) }
		val single = selectedItems.size == 1
		menu.findItem(R.id.action_hide_source)?.isVisible =
			viewModel.isHiddenHistoryModeEnabled() && single
		menu.findItem(R.id.action_hide_tag)?.isVisible =
			viewModel.isHiddenHistoryModeEnabled() && single && selectedItems.first().tags.isNotEmpty()
		return super.onPrepareActionMode(controller, mode, menu)
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_remove -> {
				viewModel.removeFromHistory(selectedItemsIds)
				mode?.finish()
				true
			}

			R.id.action_hide_from_main -> {
				viewModel.hideFromMainHistory(selectedItems)
				mode?.finish()
				true
			}

			R.id.action_show_in_main -> {
				viewModel.showInMainHistory(selectedItems)
				mode?.finish()
				true
			}

			R.id.action_hide_source -> {
				selectedItems.firstOrNull()?.let { viewModel.hideAllFromSourceOf(it) }
				mode?.finish()
				true
			}

			R.id.action_hide_tag -> {
				selectedItems.firstOrNull()?.let { viewModel.hideAllWithTagOf(it) }
				mode?.finish()
				true
			}

			R.id.action_mark_current -> {
				val itemsSnapshot = selectedItems
				buildAlertDialog(context ?: return false, isCentered = true) {
					setTitle(item.title)
					setIcon(item.icon)
					setMessage(R.string.mark_as_completed_prompt)
					setNegativeButton(android.R.string.cancel, null)
					setPositiveButton(android.R.string.ok) { _, _ ->
						viewModel.markAsRead(itemsSnapshot)
						mode?.finish()
					}
				}.show()
				true
			}

			else -> super.onActionItemClicked(controller, mode, item)
		}
	}

	override fun onCreateAdapter() = HistoryListAdapter(
		this,
		DynamicItemSizeResolver(resources, viewLifecycleOwner, settings, adjustWidth = false),
	)
}
