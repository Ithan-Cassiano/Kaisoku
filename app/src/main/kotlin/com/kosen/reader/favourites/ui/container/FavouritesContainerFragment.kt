package com.kosen.reader.favourites.ui.container

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.FlowCollector
import com.kosen.reader.R
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.BaseFragment
import com.kosen.reader.core.ui.util.ActionModeListener
import com.kosen.reader.core.ui.util.RecyclerViewOwner
import com.kosen.reader.core.ui.util.ReversibleActionObserver
import com.kosen.reader.core.util.ext.addMenuProvider
import com.kosen.reader.core.util.ext.doOnPageChanged
import com.kosen.reader.core.util.ext.findCurrentPagerFragment
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.recyclerView
import com.kosen.reader.core.util.ext.setTabsEnabled
import com.kosen.reader.core.util.ext.setTextAndVisible
import com.kosen.reader.databinding.FragmentFavouritesContainerBinding
import com.kosen.reader.databinding.ItemEmptyStateBinding
import com.kosen.reader.favourites.ui.list.FavouritesListFragment
import com.kosen.reader.history.ui.ContinueReadingSection
import com.kosen.reader.history.ui.ContinueReadingViewModel

@AndroidEntryPoint
class FavouritesContainerFragment : BaseFragment<FragmentFavouritesContainerBinding>(),
	ActionModeListener,
	RecyclerViewOwner,
	ViewStub.OnInflateListener,
	View.OnClickListener {

	private val viewModel: FavouritesContainerViewModel by viewModels()
	private val continueReadingViewModel by viewModels<ContinueReadingViewModel>()
	private var continueReadingSection: ContinueReadingSection? = null

	override val recyclerView: RecyclerView?
		get() = (findCurrentFragment() as? RecyclerViewOwner)?.recyclerView

	val categoryId: Long? get() = (findCurrentFragment() as? FavouritesListFragment)?.categoryId

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentFavouritesContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentFavouritesContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val pagerAdapter = FavouritesContainerAdapter(this)
		binding.pager.adapter = pagerAdapter
		binding.pager.offscreenPageLimit = 1
		binding.pager.recyclerView?.isNestedScrollingEnabled = false
		TabLayoutMediator(
			binding.tabs,
			binding.pager,
			FavouritesTabConfigurationStrategy(pagerAdapter, viewModel, router),
		).attach()
		binding.pager.doOnPageChanged { position ->
			pagerAdapter.getItemOrNull(position)?.let {
				viewModel.onCategorySelected(it.id)
			}
			attachContinueReadingToCurrentPage()
		}
		binding.stubEmpty.setOnInflateListener(this)
		actionModeDelegate.addListener(this)
		viewModel.categories.observe(viewLifecycleOwner, object : FlowCollector<List<FavouriteTabModel>> {
			override suspend fun emit(value: List<FavouriteTabModel>) {
				pagerAdapter.submitList(value, Runnable {
					if (viewBinding !== binding || value.isEmpty()) {
						return@Runnable
					}
					val position = viewModel.getCategoryPosition(value)
					if (binding.pager.currentItem != position) {
						binding.pager.setCurrentItem(position, false)
					}
					value.getOrNull(binding.pager.currentItem)?.let {
						viewModel.onCategorySelected(it.id)
					}
					attachContinueReadingToCurrentPage()
				})
			}
		})
		viewModel.isEmpty.observe(viewLifecycleOwner, ::onEmptyStateChanged)
		addMenuProvider(FavouritesContainerMenuProvider(router))
		viewModel.onActionDone.observeEvent(viewLifecycleOwner, ReversibleActionObserver(binding.pager))
		continueReadingSection = ContinueReadingSection(
			panel = binding.continueReadingPanel,
			viewModel = continueReadingViewModel,
			lifecycleOwner = viewLifecycleOwner,
			snackbarHost = binding.pager,
			onMangaClick = { manga -> router.openDetails(manga) },
		)
		attachContinueReadingToCurrentPage()
	}

	private fun attachContinueReadingToCurrentPage() {
		val recyclerView = (findCurrentFragment() as? RecyclerViewOwner)?.recyclerView ?: return
		continueReadingSection?.attachTo(recyclerView)
	}

	override fun onDestroyView() {
		continueReadingSection?.detach()
		continueReadingSection = null
		actionModeDelegate.removeListener(this)
		super.onDestroyView()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	override fun onActionModeStarted(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = false
			tabs.setTabsEnabled(false)
		}
	}

	override fun onActionModeFinished(mode: ActionMode) {
		viewBinding?.run {
			pager.isUserInputEnabled = true
			tabs.setTabsEnabled(true)
		}
	}

	override fun onInflate(stub: ViewStub?, inflated: View) {
		val stubBinding = ItemEmptyStateBinding.bind(inflated)
		stubBinding.icon.setImageAsync(R.drawable.ic_empty_favourites)
		stubBinding.textPrimary.setText(R.string.text_empty_holder_primary)
		stubBinding.textSecondary.setTextAndVisible(R.string.empty_favourite_categories)
		stubBinding.buttonRetry.setTextAndVisible(R.string.manage)
		stubBinding.buttonRetry.setOnClickListener(this)
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_retry -> router.openFavoriteCategories()
		}
	}

	private fun onEmptyStateChanged(isEmpty: Boolean) {
		viewBinding?.run {
			pager.isGone = isEmpty
			tabs.isGone = isEmpty
			stubEmpty.isVisible = isEmpty
		}
	}

	private fun findCurrentFragment(): Fragment? {
		return childFragmentManager.findCurrentPagerFragment(
			viewBinding?.pager ?: return null,
		)
	}
}
