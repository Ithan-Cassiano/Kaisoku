package com.kosen.reader.favourites.ui

import android.os.Bundle
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.ui.FragmentContainerActivity
import com.kosen.reader.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
