package com.kosen.reader.suggestions.ui

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.annotation.FloatRange
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.parseAsHtml
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import coil3.ImageLoader
import coil3.request.ImageRequest
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.kosen.reader.R
import com.kosen.reader.core.exceptions.CloudFlareException
import com.kosen.reader.core.exceptions.resolve.CaptchaHandler
import com.kosen.reader.core.model.distinctById
import com.kosen.reader.core.model.getLocale
import com.kosen.reader.BuildConfig
import com.kosen.reader.core.model.isAdultContent
import com.kosen.reader.core.model.isAdultContentFiltered
import com.kosen.reader.explore.domain.SourceHealthTracker
import com.kosen.reader.core.model.isNsfw
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.nav.ReaderIntent
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.util.LocaleComparator
import com.kosen.reader.core.util.ext.asArrayList
import com.kosen.reader.core.util.ext.awaitUniqueWorkInfoByName
import com.kosen.reader.core.util.ext.awaitWorkInfosByTag
import com.kosen.reader.core.util.ext.checkNotificationPermission
import com.kosen.reader.core.util.ext.flatten
import com.kosen.reader.core.util.ext.getQuantityStringSafe
import com.kosen.reader.core.util.ext.mangaSourceExtra
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.sanitize
import com.kosen.reader.core.util.ext.takeMostFrequent
import com.kosen.reader.core.util.ext.toBitmapOrNull
import com.kosen.reader.core.util.ext.trySetForeground
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.favourites.domain.FavouritesRepository
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaListFilter
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.almostEquals
import com.kosen.reader.parsers.util.runCatchingCancellable
import com.kosen.reader.parsers.util.sizeOrZero
import com.kosen.reader.settings.work.PeriodicWorkScheduler
import com.kosen.reader.suggestions.domain.MangaSuggestion
import com.kosen.reader.suggestions.domain.SuggestionRepository
import com.kosen.reader.suggestions.domain.TagsBlacklist
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.pow
import kotlin.random.Random
import androidx.appcompat.R as appcompatR

class SuggestionsWorker @AssistedInject constructor(
	@Assisted appContext: Context,
	@Assisted params: WorkerParameters,
	private val coil: ImageLoader,
	private val suggestionRepository: SuggestionRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val appSettings: AppSettings,
	private val captchaHandler: CaptchaHandler,
	private val workManager: WorkManager,
	private val mangaRepositoryFactory: MangaRepository.Factory,
	private val sourcesRepository: MangaSourcesRepository,
) : CoroutineWorker(appContext, params) {

	private val notificationManager by lazy { NotificationManagerCompat.from(appContext) }

	override suspend fun doWork(): Result {
		trySetForeground()
		if (!appSettings.isSuggestionsEnabled) {
			suggestionRepository.clear()
			return Result.success()
		}
		val count = doWorkImpl()
		val outputData = workDataOf(DATA_COUNT to count)
		return Result.success(outputData)
	}

	override suspend fun getForegroundInfo(): ForegroundInfo {
		val title = applicationContext.getString(R.string.suggestions_updating)
		val channel = NotificationChannelCompat.Builder(WORKER_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
			.setName(title)
			.setShowBadge(true)
			.setVibrationEnabled(false)
			.setSound(null, null)
			.setLightsEnabled(true)
			.build()
		notificationManager.createNotificationChannel(channel)

		val notification = NotificationCompat.Builder(applicationContext, WORKER_CHANNEL_ID)
			.setContentTitle(title)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.suggestionsSettingsIntent(applicationContext),
					0,
					false,
				),
			).addAction(
				appcompatR.drawable.abc_ic_clear_material,
				applicationContext.getString(android.R.string.cancel),
				workManager.createCancelPendingIntent(id),
			)
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.setCategory(NotificationCompat.CATEGORY_SERVICE)
			.setDefaults(0)
			.setOngoing(false)
			.setSilent(true)
			.setProgress(0, 0, true)
			.setSmallIcon(android.R.drawable.stat_notify_sync)
			.setForegroundServiceBehavior(
				if (TAG_ONESHOT in tags) {
					NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
				} else {
					NotificationCompat.FOREGROUND_SERVICE_DEFERRED
				},
			)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val actionIntent = PendingIntentCompat.getActivity(
				applicationContext, SETTINGS_ACTION_CODE,
				Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
					.putExtra(Settings.EXTRA_APP_PACKAGE, applicationContext.packageName)
					.putExtra(Settings.EXTRA_CHANNEL_ID, WORKER_CHANNEL_ID),
				0, false,
			)
			notification.addAction(
				R.drawable.ic_settings,
				applicationContext.getString(R.string.notifications_settings),
				actionIntent,
			)
		}
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(WORKER_NOTIFICATION_ID, notification.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			ForegroundInfo(WORKER_NOTIFICATION_ID, notification.build())
		}
	}

	private suspend fun doWorkImpl(): Int {
		val seed = (
			historyRepository.getList(0, 20) +
				favouritesRepository.getLastManga(20)
			).distinctById()
		val sources = getSources()
		if (sources.isEmpty()) {
			return 0
		}
		val tagsBlacklist = TagsBlacklist(appSettings.suggestionsTagsBlacklist, TAG_EQ_THRESHOLD)
		val tags = if (seed.isEmpty()) {
			emptyList()
		} else {
			seed.flatMap { it.tags.map { x -> x.title } }.takeMostFrequent(10)
		}

		val semaphore = Semaphore(MAX_PARALLELISM)
		val producer = channelFlow {
			for (it in sources) {
				if (it.isNsfw() && (appSettings.isSuggestionsExcludeNsfw || appSettings.isNsfwContentDisabled)) {
					continue
				}
				launch {
					semaphore.withPermit {
						send(getList(it, tags, tagsBlacklist))
					}
				}
			}
		}
		val suggestions = producer
			.flatten()
			.take(MAX_RAW_RESULTS)
			.map { manga ->
				MangaSuggestion(
					manga = manga,
					relevance = computeRelevance(manga.tags, tags),
				)
			}.toList()
			.sortedBy { it.relevance }
			.take(MAX_RESULTS)
		suggestionRepository.replace(suggestions)
		if (appSettings.isSuggestionsNotificationAvailable
			&& applicationContext.checkNotificationPermission(MANGA_CHANNEL_ID)
		) {
			for (i in 0..3) {
				try {
					val manga = suggestions[Random.nextInt(0, suggestions.size / 3)]
					val details = mangaRepositoryFactory.create(manga.manga.source)
						.getDetails(manga.manga)
					if (details.chapters.isNullOrEmpty()) {
						continue
					}
					if (details.rating > 0 && details.rating < RATING_MIN) {
						continue
					}
					if (details.isAdultContent() && (appSettings.isSuggestionsExcludeNsfw || appSettings.isNsfwContentDisabled)) {
						continue
					}
					if (details in tagsBlacklist) {
						continue
					}
					showNotification(details)
					break
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					e.printStackTraceDebug()
				}
			}
		}
		return suggestions.size
	}

	private suspend fun getSources(): List<MangaSource> {
		if (appSettings.isSuggestionsIncludeDisabledSources) {
			val result = sourcesRepository.allMangaSources.toMutableList<MangaSource>()
			result.addAll(sourcesRepository.getExternalSources())
			result.shuffle()
			result.sortWith(compareBy(nullsLast(LocaleComparator())) { it.getLocale() })
			return result
		} else {
			return sourcesRepository.getEnabledSources().shuffled()
		}
	}

	private suspend fun getList(
		source: MangaSource,
		tags: List<String>,
		blacklist: TagsBlacklist,
	): List<Manga> = runCatchingCancellable {
		val repository = mangaRepositoryFactory.create(source)
		val availableOrders = repository.sortOrders
		val order = preferredSortOrders.first { it in availableOrders }
		val availableTags = repository.getFilterOptions().availableTags
		val tag = tags.firstNotNullOfOrNull { title ->
			availableTags.find { x -> x !in blacklist && x.title.almostEquals(title, TAG_EQ_THRESHOLD) }
		}
		val list = repository.getList(
			offset = 0,
			order = order,
			filter = MangaListFilter(tags = setOfNotNull(tag)),
		).asArrayList()
		if (appSettings.isSuggestionsExcludeNsfw || appSettings.isNsfwContentDisabled) {
			list.removeAll { it.isAdultContentFiltered(appSettings) }
		}
		if (blacklist.isNotEmpty()) {
			list.removeAll { manga -> manga in blacklist }
		}
		list.shuffle()
		val result = list.take(MAX_SOURCE_RESULTS)
		if (result.isNotEmpty()) {
			SourceHealthTracker.recordSuccess(source)
		}
		result
	}.onFailure { e ->
		SourceHealthTracker.recordFailure(source)
		if (e is CloudFlareException) {
			// Background suggestion fetch: just record state, no headless WebView attempt — would
			// otherwise pop the captcha overlay on users mid-reading.
			captchaHandler.handle(e, tryAutoResolve = false)
		}
		e.printStackTraceDebug()
	}.getOrDefault(emptyList()).ifEmpty {
		runCatchingCancellable {
			val repository = mangaRepositoryFactory.create(source)
			val order = preferredSortOrders.firstOrNull { it in repository.sortOrders }
				?: repository.sortOrders.firstOrNull()
				?: return@runCatchingCancellable emptyList()
			val list = repository.getList(
				offset = 0,
				order = order,
				filter = MangaListFilter(),
			).asArrayList()
			if (appSettings.isSuggestionsExcludeNsfw || appSettings.isNsfwContentDisabled) {
				list.removeAll { it.isAdultContentFiltered(appSettings) }
			}
			if (blacklist.isNotEmpty()) {
				list.removeAll { manga -> manga in blacklist }
			}
			list.shuffle()
			val fallback = list.take(MAX_SOURCE_RESULTS)
			if (fallback.isNotEmpty()) {
				SourceHealthTracker.recordSuccess(source)
			}
			fallback
		}.getOrDefault(emptyList())
	}

	@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
	private suspend fun showNotification(manga: Manga) {
		val channel = NotificationChannelCompat.Builder(MANGA_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
			.setName(applicationContext.getString(R.string.suggestions))
			.setDescription(applicationContext.getString(R.string.suggestions_summary))
			.setLightsEnabled(true)
			.setShowBadge(true)
			.build()
		notificationManager.createNotificationChannel(channel)

		val id = manga.url.hashCode()
		val title = applicationContext.getString(R.string.suggestion_manga, manga.title)
		val builder = NotificationCompat.Builder(applicationContext, MANGA_CHANNEL_ID)
		val tagsText = manga.tags.joinToString(", ") { it.title }
		with(builder) {
			setContentText(tagsText)
			setContentTitle(title)
			setGroup(GROUP_SUGGESTION)
			setLargeIcon(
				coil.execute(
					ImageRequest.Builder(applicationContext)
						.data(manga.coverUrl)
						.mangaSourceExtra(manga.source)
						.build(),
				).toBitmapOrNull(),
			)
			setSmallIcon(R.drawable.ic_stat_suggestion)
			val description = manga.description?.parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT)?.sanitize()
			if (!description.isNullOrBlank()) {
				val style = NotificationCompat.BigTextStyle()
				style.bigText(
					buildSpannedString {
						append(tagsText)
						val chaptersCount = manga.chapters.sizeOrZero()
						appendLine()
						bold {
							append(
								applicationContext.resources.getQuantityStringSafe(
									R.plurals.chapters,
									chaptersCount,
									chaptersCount,
								),
							)
						}
						appendLine()
						append(description)
					},
				)
				style.setBigContentTitle(title)
				setStyle(style)
			}
			val intent = AppRouter.detailsIntent(applicationContext, manga)
			setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					id,
					intent,
					PendingIntent.FLAG_UPDATE_CURRENT,
					false,
				),
			)
			setAutoCancel(true)
			setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
			setVisibility(if (manga.isNsfw()) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
			setShortcutId(manga.id.toString())
			priority = NotificationCompat.PRIORITY_DEFAULT

			addAction(
				R.drawable.ic_read,
				applicationContext.getString(R.string.read),
				PendingIntentCompat.getActivity(
					applicationContext,
					id + 2,
					ReaderIntent.Builder(applicationContext).manga(manga).build().intent,
					0,
					false,
				),
			)

			addAction(
				R.drawable.ic_suggestion,
				applicationContext.getString(R.string.more),
				PendingIntentCompat.getActivity(
					applicationContext,
					0,
					AppRouter.suggestionsIntent(applicationContext),
					0,
					false,
				),
			)
		}
		notificationManager.notify(TAG, id, builder.build())
	}

	@FloatRange(from = 0.0, to = 1.0)
	private fun computeRelevance(mangaTags: Set<MangaTag>, allTags: List<String>): Float {
		val maxWeight = (allTags.size + allTags.size + 1 - mangaTags.size) * mangaTags.size / 2.0
		val weight = mangaTags.sumOf { tag ->
			val index = allTags.inexactIndexOf(tag.title, TAG_EQ_THRESHOLD)
			if (index < 0) 0 else allTags.size - index
		}
		return (weight / maxWeight).pow(2.0).toFloat()
	}

	private fun Iterable<String>.inexactIndexOf(element: String, threshold: Float): Int {
		forEachIndexed { i, t ->
			if (t.almostEquals(element, threshold)) {
				return i
			}
		}
		return -1
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		private val settings: AppSettings,
	) : PeriodicWorkScheduler {

		override suspend fun schedule() {
			val request = PeriodicWorkRequestBuilder<SuggestionsWorker>(6, TimeUnit.HOURS)
				.setConstraints(createConstraints())
				.addTag(TAG)
				.setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
				.build()
			workManager
				.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
				.await()
		}

		override suspend fun unschedule() {
			workManager
				.cancelUniqueWork(TAG)
				.await()
		}

		override suspend fun isScheduled(): Boolean {
			return workManager
				.awaitUniqueWorkInfoByName(TAG)
				.any { !it.state.isFinished }
		}

		suspend fun startNow() {
			if (workManager.awaitWorkInfosByTag(TAG_ONESHOT).any { !it.state.isFinished }) {
				return
			}
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()
			val request = OneTimeWorkRequestBuilder<SuggestionsWorker>()
				.setConstraints(constraints)
				.addTag(TAG_ONESHOT)
				.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
				.build()
			workManager.enqueue(request).await()
		}

		private fun createConstraints() = Constraints.Builder()
			.setRequiredNetworkType(if (settings.isSuggestionsWiFiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
			.setRequiresBatteryNotLow(true)
			.build()
	}

	private companion object {

		const val TAG = "suggestions"
		const val TAG_ONESHOT = "suggestions_oneshot"
		const val DATA_COUNT = "count"
		const val WORKER_CHANNEL_ID = "suggestion_worker"
		const val MANGA_CHANNEL_ID = "suggestions"
		const val GROUP_SUGGESTION = "com.kosen.reader.SUGGESTIONS"
		const val WORKER_NOTIFICATION_ID = 36
		const val MAX_RESULTS = 160
		const val MAX_PARALLELISM = 3
		const val MAX_SOURCE_RESULTS = 20
		const val MAX_RAW_RESULTS = 280
		const val TAG_EQ_THRESHOLD = 0.4f
		const val RATING_MIN = 0.5f
		const val SETTINGS_ACTION_CODE = 4

		val preferredSortOrders = listOf(
			SortOrder.UPDATED,
			SortOrder.NEWEST,
			SortOrder.POPULARITY,
			SortOrder.RATING,
		)
	}
}
