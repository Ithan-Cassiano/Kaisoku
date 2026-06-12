package com.kosen.reader.core.nav

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.kosen.reader.core.model.parcelable.ParcelableManga
import com.kosen.reader.core.util.ext.getParcelableExtraCompat
import com.kosen.reader.parsers.model.Manga

class PickMangaContract : ActivityResultContract<String, Manga?>() {

	override fun createIntent(context: Context, input: String): Intent =
		AppRouter.pickMangaIntent(context, input)

	override fun parseResult(resultCode: Int, intent: Intent?): Manga? {
		if (resultCode != Activity.RESULT_OK || intent == null) return null
		return intent.getParcelableExtraCompat<ParcelableManga>(AppRouter.KEY_MANGA)?.manga
	}
}
