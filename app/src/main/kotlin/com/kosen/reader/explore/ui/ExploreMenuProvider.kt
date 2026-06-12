package com.kosen.reader.explore.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import com.kosen.reader.R
import com.kosen.reader.core.nav.AppRouter

class ExploreMenuProvider(
	private val router: AppRouter,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_explore, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_add_mihon_sources -> {
				router.openMihonExtensionRepos(showAddDialog = true)
				true
			}

			R.id.action_manage -> {
				router.openSourcesSettings()
				true
			}

			else -> false
		}
	}
}
