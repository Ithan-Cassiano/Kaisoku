package com.kosen.reader.core.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import coil3.ImageLoader
import dagger.Lazy
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import com.kosen.reader.core.exceptions.resolve.CaptchaHandler
import com.kosen.reader.core.network.MangaHttpClient
import com.kosen.reader.core.network.imageproxy.ImageProxyInterceptor
import com.kosen.reader.core.parser.MangaDataRepository
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.download.ui.worker.DownloadNotificationFactory
import com.kosen.reader.download.ui.worker.DownloadSlowdownDispatcher
import com.kosen.reader.download.ui.worker.DownloadWorker
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.favourites.domain.FavouritesRepository
import com.kosen.reader.history.data.HistoryRepository
import com.kosen.reader.local.data.LocalMangaRepository
import com.kosen.reader.local.data.LocalStorageCache
import com.kosen.reader.local.data.LocalStorageChanges
import com.kosen.reader.local.data.PageCache
import com.kosen.reader.local.domain.DeleteReadChaptersUseCase
import com.kosen.reader.local.domain.MangaLock
import com.kosen.reader.local.domain.model.LocalManga
import com.kosen.reader.local.ui.LocalStorageCleanupWorker
import com.kosen.reader.suggestions.domain.SuggestionRepository
import com.kosen.reader.suggestions.ui.SuggestionsWorker
import com.kosen.reader.tracker.domain.CheckNewChaptersUseCase
import com.kosen.reader.tracker.domain.GetTracksUseCase
import com.kosen.reader.tracker.domain.SmartTrackerNotificationFilter
import com.kosen.reader.tracker.work.TrackWorker
import com.kosen.reader.tracker.work.TrackerNotificationHelper
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AppWorkerFactory @Inject constructor(
	@MangaHttpClient private val okHttp: Provider<OkHttpClient>,
	@PageCache private val cache: Provider<LocalStorageCache>,
	private val localMangaRepository: Provider<LocalMangaRepository>,
	private val mangaLock: Provider<MangaLock>,
	private val mangaDataRepository: Provider<MangaDataRepository>,
	private val mangaRepositoryFactory: Provider<MangaRepository.Factory>,
	private val settings: Provider<AppSettings>,
	@LocalStorageChanges private val localStorageChanges: Provider<MutableSharedFlow<LocalManga?>>,
	private val slowdownDispatcher: Provider<DownloadSlowdownDispatcher>,
	private val imageProxyInterceptor: Provider<ImageProxyInterceptor>,
	private val downloadNotificationFactoryFactory: Provider<DownloadNotificationFactory.Factory>,
	private val captchaHandler: Provider<CaptchaHandler>,
	private val trackerNotificationHelper: Provider<TrackerNotificationHelper>,
	private val getTracksUseCase: Provider<GetTracksUseCase>,
	private val checkNewChaptersUseCase: Provider<CheckNewChaptersUseCase>,
	private val smartNotificationFilter: Provider<SmartTrackerNotificationFilter>,
	private val workManager: Provider<WorkManager>,
	private val localRepositoryLazy: Lazy<LocalMangaRepository>,
	private val downloadSchedulerLazy: Lazy<DownloadWorker.Scheduler>,
	private val deleteReadChaptersUseCase: Provider<DeleteReadChaptersUseCase>,
	private val coil: Provider<ImageLoader>,
	private val suggestionRepository: Provider<SuggestionRepository>,
	private val historyRepository: Provider<HistoryRepository>,
	private val favouritesRepository: Provider<FavouritesRepository>,
	private val sourcesRepository: Provider<MangaSourcesRepository>,
) : WorkerFactory() {

	override fun createWorker(
		appContext: Context,
		workerClassName: String,
		workerParameters: WorkerParameters,
	): ListenableWorker? = when (workerClassName) {
		DownloadWorker::class.java.name -> DownloadWorker(
			appContext = appContext,
			params = workerParameters,
			okHttp = okHttp.get(),
			cache = cache.get(),
			localMangaRepository = localMangaRepository.get(),
			mangaLock = mangaLock.get(),
			mangaDataRepository = mangaDataRepository.get(),
			mangaRepositoryFactory = mangaRepositoryFactory.get(),
			settings = settings.get(),
			localStorageChanges = localStorageChanges.get(),
			slowdownDispatcher = slowdownDispatcher.get(),
			imageProxyInterceptor = imageProxyInterceptor.get(),
			notificationFactoryFactory = downloadNotificationFactoryFactory.get(),
		)

		TrackWorker::class.java.name -> TrackWorker(
			context = appContext,
			workerParams = workerParameters,
			captchaHandler = captchaHandler.get(),
			notificationHelper = trackerNotificationHelper.get(),
			settings = settings.get(),
			getTracksUseCase = getTracksUseCase.get(),
			checkNewChaptersUseCase = checkNewChaptersUseCase.get(),
			smartNotificationFilter = smartNotificationFilter.get(),
			workManager = workManager.get(),
			localRepositoryLazy = localRepositoryLazy,
			downloadSchedulerLazy = downloadSchedulerLazy,
		)

		LocalStorageCleanupWorker::class.java.name -> LocalStorageCleanupWorker(
			appContext = appContext,
			params = workerParameters,
			settings = settings.get(),
			localMangaRepository = localMangaRepository.get(),
			dataRepository = mangaDataRepository.get(),
			deleteReadChaptersUseCase = deleteReadChaptersUseCase.get(),
		)

		SuggestionsWorker::class.java.name -> SuggestionsWorker(
			appContext = appContext,
			params = workerParameters,
			coil = coil.get(),
			suggestionRepository = suggestionRepository.get(),
			historyRepository = historyRepository.get(),
			favouritesRepository = favouritesRepository.get(),
			appSettings = settings.get(),
			captchaHandler = captchaHandler.get(),
			workManager = workManager.get(),
			mangaRepositoryFactory = mangaRepositoryFactory.get(),
			sourcesRepository = sourcesRepository.get(),
		)

		else -> null
	}
}
