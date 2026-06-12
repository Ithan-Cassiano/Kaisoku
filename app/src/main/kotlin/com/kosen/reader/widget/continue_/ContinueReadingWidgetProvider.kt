package com.kosen.reader.widget.continue_

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.executeBlocking
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.RoundedCornersTransformation
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import com.kosen.reader.R
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.nav.ReaderIntent
import com.kosen.reader.core.prefs.AppWidgetConfig
import com.kosen.reader.core.ui.BaseAppWidgetProvider
import com.kosen.reader.core.util.ext.getDrawableOrThrow
import com.kosen.reader.core.util.ext.mangaExtra
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.reader.ui.ReaderActivity

class ContinueReadingWidgetProvider : BaseAppWidgetProvider() {

	override fun onUpdateWidget(context: Context, config: AppWidgetConfig): RemoteViews {
		val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
		val entryPoint = EntryPointAccessors.fromApplication(context, ContinueWidgetEntryPoint::class.java)
		val historyRepository = entryPoint.historyRepository()
		val coil = entryPoint.imageLoader()
		val manga = runBlocking { historyRepository.getContinueReadingMangaOrNull() }
		if (manga == null) {
			views.setTextViewText(R.id.textView_title, context.getString(R.string.continue_widget_empty))
			views.setTextViewText(R.id.textView_subtitle, context.getString(R.string.continue_reading))
			views.setImageViewResource(R.id.imageView_cover, R.drawable.ic_placeholder)
			return views
		}
		views.setTextViewText(R.id.textView_title, manga.title)
		val progress = runBlocking { historyRepository.getProgress(manga.id, entryPoint.appSettings().progressIndicatorMode) }
		val subtitle = progress?.let {
			context.getString(R.string.continue_reading) + " · " + ReadingProgress.percentToString(it.percent) + "%"
		} ?: context.getString(R.string.continue_reading)
		views.setTextViewText(R.id.textView_subtitle, subtitle)
		val coverSize = Size(
			context.resources.getDimensionPixelSize(R.dimen.widget_cover_width),
			context.resources.getDimensionPixelSize(R.dimen.widget_cover_height),
		)
		val transformation = RoundedCornersTransformation(
			context.resources.getDimension(R.dimen.appwidget_corner_radius_inner),
		)
		runCatchingCancellable {
			coil.executeBlocking(
				ImageRequest.Builder(context)
					.data(manga.coverUrl)
					.size(coverSize)
					.mangaExtra(manga)
					.transformations(transformation)
					.build(),
			).getDrawableOrThrow().toBitmap()
		}.onSuccess { bitmap ->
			views.setImageViewBitmap(R.id.imageView_cover, bitmap)
		}.onFailure {
			views.setImageViewResource(R.id.imageView_cover, R.drawable.ic_placeholder)
		}
		val intent = Intent(context, ReaderActivity::class.java).apply {
			action = ReaderIntent.ACTION_MANGA_READ
			putExtra(AppRouter.KEY_ID, manga.id)
		}
		views.setOnClickPendingIntent(
			R.id.widget_root,
			PendingIntentCompat.getActivity(
				context,
				manga.id.toInt(),
				intent,
				PendingIntent.FLAG_UPDATE_CURRENT,
				true,
			),
		)
		return views
	}
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ContinueWidgetEntryPoint {
	fun historyRepository(): HistoryRepository
	fun imageLoader(): ImageLoader
	fun appSettings(): com.kosen.reader.core.prefs.AppSettings
}
