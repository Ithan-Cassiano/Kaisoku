package com.kosen.reader.history.ui

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.kosen.reader.R
import com.kosen.reader.core.ui.FragmentContainerActivity
import com.kosen.reader.history.domain.PrivateHistoryVolumeShortcut
import javax.inject.Inject

@AndroidEntryPoint
class HistoryActivity : FragmentContainerActivity(HistoryListFragment::class.java) {

	@Inject lateinit var privateHistoryVolumeShortcut: PrivateHistoryVolumeShortcut

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		lifecycleScope.launch {
			privateHistoryVolumeShortcut.triggers.collect {
				(supportFragmentManager.findFragmentById(R.id.container) as? HistoryListFragment)
					?.openPrivateHistoryViaShortcut()
			}
		}
	}
}
