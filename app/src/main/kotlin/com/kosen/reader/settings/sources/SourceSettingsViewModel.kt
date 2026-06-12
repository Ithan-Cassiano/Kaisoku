package com.kosen.reader.settings.sources

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl
import com.kosen.reader.R
import com.kosen.reader.core.model.MangaSource
import com.kosen.reader.core.nav.AppRouter
import com.kosen.reader.core.network.cookies.MutableCookieJar
import com.kosen.reader.core.parser.CachingMangaRepository
import com.kosen.reader.core.parser.MangaRepository
import com.kosen.reader.core.parser.ParserMangaRepository
import com.kosen.reader.settings.sources.auth.asSourceAuthRepository
import com.kosen.reader.core.parser.PluginMangaRepository
import com.kosen.reader.core.prefs.SourceSettings
import com.kosen.reader.core.ui.BaseViewModel
import com.kosen.reader.core.ui.util.ReversibleAction
import com.kosen.reader.core.util.ext.MutableEventFlow
import com.kosen.reader.core.util.ext.call
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.explore.domain.SourceHealthTestUseCase
import com.kosen.reader.parsers.MangaParserAuthProvider
import com.kosen.reader.parsers.exception.AuthRequiredException
import javax.inject.Inject

@HiltViewModel
class SourceSettingsViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	savedStateHandle: SavedStateHandle,
	mangaRepositoryFactory: MangaRepository.Factory,
	private val cookieJar: MutableCookieJar,
	private val mangaSourcesRepository: MangaSourcesRepository,
	private val sourceHealthTestUseCase: SourceHealthTestUseCase,
) : BaseViewModel(), SharedPreferences.OnSharedPreferenceChangeListener {

	val onSourceTestResult = MutableEventFlow<String>()

	val source = MangaSource(savedStateHandle.get<String>(AppRouter.KEY_SOURCE))
	val repository = mangaRepositoryFactory.create(source)

	val onActionDone = MutableEventFlow<ReversibleAction>()
	val username = MutableStateFlow<String?>(null)
	val isAuthorized = MutableStateFlow<Boolean?>(null)
	val browserUrl = MutableStateFlow<String?>(null)
	val isEnabled = mangaSourcesRepository.observeIsEnabled(source)
	private var usernameLoadJob: Job? = null

	init {
		when (repository) {
			is ParserMangaRepository -> {
				browserUrl.value = "https://${repository.domain}"
				repository.getConfig().subscribe(this)
				loadUsername(repository.getAuthProvider())
			}
			is PluginMangaRepository -> {
				browserUrl.value = "https://${repository.domain}"
				repository.getConfig().subscribe(this)
				loadUsername(repository.getAuthProvider())
			}
		}
	}

	override fun onCleared() {
		when (repository) {
			is ParserMangaRepository -> {
				repository.getConfig().unsubscribe(this)
			}
			is PluginMangaRepository -> {
				repository.getConfig().unsubscribe(this)
			}
		}
		super.onCleared()
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
		if (repository is CachingMangaRepository) {
			if (key != SourceSettings.KEY_SLOWDOWN && key != SourceSettings.KEY_SORT_ORDER) {
				repository.invalidateCache()
			}
		}
		if (repository is ParserMangaRepository) {
			if (key == SourceSettings.KEY_DOMAIN) {
				browserUrl.value = "https://${repository.domain}"
			}
		} else if (repository is PluginMangaRepository) {
			if (key == SourceSettings.KEY_DOMAIN) {
				browserUrl.value = "https://${repository.domain}"
			}
		}
	}

	fun onResume() {
		if (usernameLoadJob?.isActive == true) {
			return
		}
		when (repository) {
			is ParserMangaRepository -> loadUsername(repository.getAuthProvider())
			is PluginMangaRepository -> loadUsername(repository.getAuthProvider())
		}
	}

	fun clearCookies() {
		val domain = when (repository) {
			is ParserMangaRepository -> repository.domain
			is PluginMangaRepository -> repository.domain
			else -> return
		}
		launchLoadingJob(Dispatchers.Default) {
			val url = HttpUrl.Builder()
				.scheme("https")
				.host(domain)
				.build()
			cookieJar.removeCookies(url, null)
			repository.asSourceAuthRepository()?.setAuthSessionConfirmed(false)
			onActionDone.call(ReversibleAction(R.string.cookies_cleared, null))
			when (repository) {
				is ParserMangaRepository -> loadUsername(repository.getAuthProvider())
				is PluginMangaRepository -> loadUsername(repository.getAuthProvider())
			}
		}
	}

	fun setEnabled(value: Boolean) {
		launchJob(Dispatchers.Default) {
			mangaSourcesRepository.setSourcesEnabled(setOf(source), value)
		}
	}

	fun testSource() {
		launchLoadingJob(Dispatchers.Default) {
			val result = sourceHealthTestUseCase.runTests(source)
			val message = if (result.isFullyOk) {
				context.getString(R.string.test_source_ok)
			} else {
				val failed = buildList {
					if (!result.listOk) add(R.string.test_source_step_list)
					if (!result.detailsOk) add(R.string.test_source_step_details)
					if (!result.pagesOk) add(R.string.test_source_step_pages)
				}.joinToString(", ") { context.getString(it) }
				context.getString(R.string.test_source_fail, failed)
			}
			onSourceTestResult.call(message)
		}
	}

	private fun loadUsername(authProvider: MangaParserAuthProvider?) {
		launchLoadingJob(Dispatchers.Default) {
			username.value = null
			isAuthorized.value = null
			val authorized = authProvider?.isAuthorized() ?: false
			isAuthorized.value = authorized
			username.value = if (authorized) {
				runCatching { authProvider?.getUsername() }.getOrNull()
			} else {
				null
			}
		}
	}
}
