package com.kosen.reader.history.ui



import android.os.Bundle

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import androidx.core.view.isVisible

import androidx.fragment.app.viewModels

import androidx.lifecycle.Lifecycle

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import dagger.hilt.android.AndroidEntryPoint

import com.kosen.reader.R

import com.kosen.reader.core.nav.router

import com.kosen.reader.core.util.ext.observe

import com.kosen.reader.databinding.FragmentHistoryHomeBinding



@AndroidEntryPoint

open class HistoryHomeFragment : HistoryListFragment(), HistoryHomeUiHost {



	private var homeBinding: FragmentHistoryHomeBinding? = null



	override fun onCreateViewBinding(

		inflater: LayoutInflater,

		container: ViewGroup?,

	): com.kosen.reader.databinding.FragmentListBinding {

		val home = FragmentHistoryHomeBinding.inflate(inflater, container, false)

		homeBinding = home

		return home.listContainer

	}



	override fun onCreateFragmentRootView(binding: com.kosen.reader.databinding.FragmentListBinding): View {

		return homeBinding?.root ?: binding.root

	}



	override fun onViewBindingCreated(

		binding: com.kosen.reader.databinding.FragmentListBinding,

		savedInstanceState: Bundle?,

	) {

		super.onViewBindingCreated(binding, savedInstanceState)

		val home = homeBinding ?: return

		home.buttonBackPrivateHistory.setOnClickListener { exitPrivateHistoryView() }

		home.textViewStatsHint.setOnClickListener { viewModel.applyWeeklyReadFilter() }

		viewModel.weeklyReadingMinutes.observe(viewLifecycleOwner, Lifecycle.State.STARTED) { minutes ->

			home.textViewStatsHint.isVisible = minutes != null && minutes > 0 &&

				!viewModel.isPrivateHistoryFilterActive.value

			if (minutes != null && minutes > 0) {

				home.textViewStatsHint.text = getString(R.string.history_stats_hint, minutes)

			}

		}

		if (settings.isTipEnabled(TIP_HISTORY)) {

			showHistoryOnboarding()

		}

	}



	override fun onPrivateHistoryFilterActiveChanged(isActive: Boolean) {

		val home = homeBinding ?: return

		home.bannerPrivateHistory.isVisible = isActive

		home.buttonBackPrivateHistory.isVisible = isActive

		home.textViewPageTitle.setText(

			if (isActive) R.string.private_history_filter_revealed else R.string.history_home_title,

		)

	}



	override fun onDestroyView() {

		homeBinding = null

		super.onDestroyView()

	}



	private fun showHistoryOnboarding() {

		MaterialAlertDialogBuilder(requireContext())

			.setTitle(R.string.onboarding_history_title)

			.setMessage(R.string.onboarding_history_message)

			.setPositiveButton(android.R.string.ok) { _, _ -> settings.closeTip(TIP_HISTORY) }

			.setNegativeButton(R.string.skip) { _, _ -> settings.closeTip(TIP_HISTORY) }

			.show()

	}



	private companion object {

		const val TIP_HISTORY = "history_tour"

	}

}

