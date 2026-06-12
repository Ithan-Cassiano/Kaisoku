package com.kosen.reader.history.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.databinding.FragmentDevHistoryHomeBinding

@AndroidEntryPoint
class DevHistoryHomeFragment : HistoryListFragment(), HistoryHomeUiHost {

	private var devBinding: FragmentDevHistoryHomeBinding? = null

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	): com.kosen.reader.databinding.FragmentListBinding {
		val dev = FragmentDevHistoryHomeBinding.inflate(inflater, container, false)
		devBinding = dev
		return dev.listContainer
	}

	override fun onCreateFragmentRootView(binding: com.kosen.reader.databinding.FragmentListBinding): View {
		return devBinding?.root ?: binding.root
	}

	override fun onDestroyView() {
		devBinding = null
		super.onDestroyView()
	}
}
