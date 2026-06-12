package com.kosen.reader.main.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.kosen.reader.R
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.explore.ui.ExploreFragment
import com.kosen.reader.favourites.ui.container.FavouritesContainerFragment
import com.kosen.reader.favourites.ui.list.FavouritesListFragment
import com.kosen.reader.history.ui.HistoryHomeUiHost
import com.kosen.reader.tracker.ui.feed.FeedFragment
import com.google.android.material.R as materialR

object DevTabBackground {

	private val mainActivities = mutableSetOf<MainActivity>()

	fun install(application: Application) {
		application.registerActivityLifecycleCallbacks(DevTabBackgroundActivityCallbacks())
	}

	fun update(activity: MainActivity) {
		if (isEnabled(activity)) {
			applyMainActivityBackground(activity)
			refreshFragmentBackgrounds(activity)
		} else {
			clearMainActivityBackground(activity)
			refreshFragmentBackgrounds(activity)
		}
	}

	fun updateVisibleMainActivities(context: Context) {
		mainActivities.toList().forEach { update(it) }
	}

	private fun isEnabled(context: Context): Boolean {
		return EntryPointAccessors.fromApplication(
			context.applicationContext,
			DevTabBackgroundEntryPoint::class.java,
		).appSettings().isDevTabBackgroundEnabled
	}

	private class DevTabBackgroundActivityCallbacks : Application.ActivityLifecycleCallbacks {

		private val fragmentCallbacks = DevTabBackgroundFragmentCallbacks()

		override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
			if (activity is MainActivity) {
				mainActivities.add(activity)
				update(activity)
			}
			(activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
				fragmentCallbacks,
				true,
			)
		}

		override fun onActivityStarted(activity: Activity) = Unit

		override fun onActivityResumed(activity: Activity) {
			if (activity is MainActivity) {
				update(activity)
			}
		}

		override fun onActivityPaused(activity: Activity) = Unit

		override fun onActivityStopped(activity: Activity) = Unit

		override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

		override fun onActivityDestroyed(activity: Activity) {
			if (activity is MainActivity) {
				mainActivities.remove(activity)
			}
		}
	}

	private class DevTabBackgroundFragmentCallbacks : FragmentManager.FragmentLifecycleCallbacks() {

		override fun onFragmentViewCreated(
			fm: FragmentManager,
			f: Fragment,
			v: View,
			savedInstanceState: Bundle?,
		) {
			if (!isEnabled(f.requireContext()) || !shouldApply(f)) {
				return
			}
			makeListsTransparent(v)
		}
	}

	private fun shouldApply(fragment: Fragment): Boolean = when (fragment) {
		is HistoryHomeUiHost,
		is FavouritesContainerFragment,
		is FavouritesListFragment,
		is ExploreFragment,
		is FeedFragment,
		-> true
		else -> false
	}

	private fun refreshFragmentBackgrounds(activity: FragmentActivity) {
		activity.supportFragmentManager.fragments.forEach { refreshFragmentTree(it, activity) }
	}

	private fun refreshFragmentTree(fragment: Fragment, activity: FragmentActivity) {
		fragment.view?.let { view ->
			if (shouldApply(fragment)) {
				if (isEnabled(activity)) {
					makeListsTransparent(view)
				} else {
					restoreListBackgrounds(view)
				}
			}
		}
		fragment.childFragmentManager.fragments.forEach { refreshFragmentTree(it, activity) }
	}

	private fun makeListsTransparent(root: View) {
		root.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
			?.setBackgroundResource(android.R.color.transparent)
		root.findViewById<RecyclerView>(R.id.recyclerView)
			?.setBackgroundResource(android.R.color.transparent)
		root.allViews.forEach { view ->
			if (view is SwipeRefreshLayout || view is RecyclerView) {
				view.setBackgroundResource(android.R.color.transparent)
			}
		}
	}

	private fun restoreListBackgrounds(root: View) {
		val backgroundColor = MaterialColors.getColor(root, materialR.attr.colorSurface)
		root.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
			?.setBackgroundColor(backgroundColor)
		root.findViewById<RecyclerView>(R.id.recyclerView)
			?.setBackgroundColor(backgroundColor)
		root.allViews.forEach { view ->
			if (view is SwipeRefreshLayout || view is RecyclerView) {
				view.setBackgroundColor(backgroundColor)
			}
		}
	}

	private fun applyMainActivityBackground(activity: MainActivity) {
		activity.window.decorView.post {
			val container = activity.viewBinding.container
			val contentRoot = (container.parent as? View) ?: activity.viewBinding.root
			contentRoot.setBackgroundResource(R.drawable.bg_dev_tab_background)
			container.setBackgroundResource(android.R.color.transparent)
			if (contentRoot is CoordinatorLayout) {
				contentRoot.clipChildren = false
				contentRoot.clipToPadding = false
			}
		}
	}

	private fun clearMainActivityBackground(activity: MainActivity) {
		activity.window.decorView.post {
			val container = activity.viewBinding.container
			val contentRoot = (container.parent as? View) ?: activity.viewBinding.root
			contentRoot.background = null
			container.background = null
			if (contentRoot is CoordinatorLayout) {
				contentRoot.clipChildren = true
				contentRoot.clipToPadding = true
			}
		}
	}
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevTabBackgroundEntryPoint {
	fun appSettings(): AppSettings
}
