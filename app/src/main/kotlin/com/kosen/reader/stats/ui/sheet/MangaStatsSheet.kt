package com.kosen.reader.stats.ui.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.IntList
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import com.kosen.reader.R
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.ui.sheet.BaseAdaptiveSheet
import com.kosen.reader.core.util.KosenColors
import com.kosen.reader.core.util.ext.consume
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.textAndVisible
import com.kosen.reader.databinding.SheetStatsMangaBinding
import com.kosen.reader.parsers.util.format
import com.kosen.reader.stats.ui.views.BarChartView

@AndroidEntryPoint
class MangaStatsSheet : BaseAdaptiveSheet<SheetStatsMangaBinding>(), View.OnClickListener {

	private val viewModel: MangaStatsViewModel by viewModels()

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetStatsMangaBinding {
		return SheetStatsMangaBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetStatsMangaBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.textViewTitle.text = viewModel.manga.title
		binding.chartView.barColor = KosenColors.ofManga(binding.root.context, viewModel.manga)
		viewModel.stats.observe(viewLifecycleOwner, ::onStatsChanged)
		viewModel.startDate.observe(viewLifecycleOwner) {
			binding.textViewStart.textAndVisible = it?.format(binding.root.context)
		}
		viewModel.totalPagesRead.observe(viewLifecycleOwner) {
			binding.textViewPages.text = getString(R.string.pages_read_s, it.format())
		}
		binding.buttonOpen.setOnClickListener(this)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onClick(v: View) {
		router.openDetails(viewModel.manga)
	}

	private fun onStatsChanged(stats: IntList) {
		val chartView = viewBinding?.chartView ?: return
		if (stats.isEmpty()) {
			chartView.setData(emptyList())
			return
		}
		val bars = ArrayList<BarChartView.Bar>(stats.size)
		stats.forEach { pages ->
			bars.add(
				BarChartView.Bar(
					value = pages,
					label = pages.toString(),
				),
			)
		}
		chartView.setData(bars)
	}
}
