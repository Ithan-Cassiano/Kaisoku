package com.kosen.reader.main.ui

import android.Manifest
import android.app.BackgroundServiceStartNotAllowedException
import android.app.ServiceStartNotAllowedException
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import androidx.core.view.MenuProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
import com.google.android.material.appbar.AppBarLayout.LayoutParams.SCROLL_FLAG_SNAP
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.search.SearchView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kosen.reader.BuildConfig
import com.kosen.reader.R
import com.kosen.reader.backups.ui.periodical.PeriodicalBackupService
import com.kosen.reader.browser.AdListUpdateService
import com.kosen.reader.core.exceptions.resolve.SnackbarErrorObserver
import com.kosen.reader.core.github.AppVersion
import com.kosen.reader.core.ui.dialog.showIncognitoModePicker
import com.kosen.reader.core.nav.router
import com.kosen.reader.core.os.VoiceInputContract
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.core.prefs.NavItem
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.ui.util.FadingAppbarMediator
import com.kosen.reader.core.ui.util.MenuInvalidator
import com.kosen.reader.core.ui.widgets.SlidingBottomNavigationView
import com.kosen.reader.core.util.ext.consume
import com.kosen.reader.core.util.ext.end
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.printStackTraceDebug
import com.kosen.reader.core.util.ext.start
import com.kosen.reader.databinding.ActivityMainBinding
import com.kosen.reader.details.service.MangaPrefetchService
import com.kosen.reader.favourites.ui.container.FavouritesContainerFragment
import com.kosen.reader.core.prefs.IncognitoMode
import com.kosen.reader.history.domain.PrivateHistoryVolumeShortcut
import com.kosen.reader.history.ui.HistoryHomeUiHost
import com.kosen.reader.history.ui.HistoryListFragment
import com.kosen.reader.local.ui.LocalIndexUpdateService
import com.kosen.reader.local.ui.LocalStorageCleanupWorker
import com.kosen.reader.main.ui.owners.AppBarOwner
import com.kosen.reader.main.ui.owners.BottomNavOwner
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.remotelist.ui.MangaSearchMenuProvider
import com.kosen.reader.search.ui.suggestion.SearchSuggestionItemCallback
import com.kosen.reader.settings.sources.manage.plugins.UpdatePluginsProvider
import com.kosen.reader.search.ui.suggestion.SearchSuggestionListenerImpl
import com.kosen.reader.search.ui.suggestion.SearchSuggestionMenuProvider
import com.kosen.reader.search.ui.suggestion.SearchSuggestionViewModel
import com.kosen.reader.search.ui.suggestion.adapter.SearchSuggestionAdapter
import javax.inject.Inject
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), AppBarOwner, BottomNavOwner,
	View.OnClickListener,
	SearchSuggestionItemCallback.SuggestionItemListener,
	MainNavigationDelegate.OnFragmentChangedListener,
	View.OnLayoutChangeListener,
	SearchView.TransitionListener {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var updatePluginsProvider: UpdatePluginsProvider

	@Inject
	lateinit var privateHistoryVolumeShortcut: PrivateHistoryVolumeShortcut

	private val viewModel by viewModels<MainViewModel>()
	private val searchSuggestionViewModel by viewModels<SearchSuggestionViewModel>()
	private val voiceInputLauncher = registerForActivityResult(VoiceInputContract()) { result ->
		if (result != null) {
			viewBinding.searchView.setText(result)
		}
	}
	private lateinit var navigationDelegate: MainNavigationDelegate
	private lateinit var fadingAppbarMediator: FadingAppbarMediator

	override val appBar: AppBarLayout
		get() = viewBinding.appbar

	override val bottomNav: SlidingBottomNavigationView?
		get() = viewBinding.bottomNav

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityMainBinding.inflate(layoutInflater))
		setSupportActionBar(viewBinding.searchBar)

		viewBinding.fab?.setOnClickListener(this)
		viewBinding.navRail?.headerView?.findViewById<View>(R.id.railFab)?.setOnClickListener(this)
		fadingAppbarMediator =
			FadingAppbarMediator(viewBinding.appbar, viewBinding.layoutSearch ?: viewBinding.searchBar)

		navigationDelegate = MainNavigationDelegate(
			navBar = checkNotNull(bottomNav ?: viewBinding.navRail),
			fragmentManager = supportFragmentManager,
			settings = settings,
		)
		navigationDelegate.addOnFragmentChangedListener(this)
		navigationDelegate.onCreate(this, savedInstanceState)
		if (settings.incognitoMode == IncognitoMode.HIDDEN_HISTORY) {
			settings.isPrivateHistoryVolumeShortcutEnabled = true
		}
		viewBinding.textViewTitle?.let { tv ->
			navigationDelegate.observeTitle().observe(this) { tv.text = it }
		}

		addMenuProvider(MainMenuProvider(router, viewModel))

		val exitCallback = ExitCallback(this, viewBinding.container)
		onBackPressedDispatcher.addCallback(exitCallback)
		onBackPressedDispatcher.addCallback(navigationDelegate)

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !resources.getBoolean(R.bool.is_predictive_back_enabled)) {
			val legacySearchCallback = SearchViewLegacyBackCallback(viewBinding.searchView)
			viewBinding.searchView.addTransitionListener(legacySearchCallback)
			onBackPressedDispatcher.addCallback(legacySearchCallback)
		}

		if (savedInstanceState == null) {
			onFirstStart()
		}

		viewModel.onOpenReader.observeEvent(this, this::onOpenReader)
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.container, null))
		viewModel.isLoading.observe(this, this::onLoadingStateChanged)
		viewModel.isResumeEnabled.observe(this, this::onResumeEnabledChanged)

		lifecycleScope.launch {
			privateHistoryVolumeShortcut.triggers.collect {
				if (settings.incognitoMode != IncognitoMode.HIDDEN_HISTORY) {
					return@collect
				}
				navigationDelegate.openHistoryTab(suppressReselectAction = true)
				withResumed {
					(navigationDelegate.primaryFragment as? HistoryListFragment)
						?.openPrivateHistoryViaShortcut()
				}
			}
		}
		viewModel.feedCounter.observe(this, ::onFeedCounterChanged)
		viewModel.appUpdate.observe(this, MenuInvalidator(this))
		viewModel.onFirstStart.observeEvent(this) { router.showWelcomeSheet() }
		viewModel.onShowIncognitoModePicker.observeEvent(this) {
			openIncognitoModePicker()
		}
		viewModel.onUpdateAvailable.observeEvent(this, ::showUpdateAvailableDialog)
		viewModel.onUpdateCheckMessage.observeEvent(this) { message ->
			com.google.android.material.snackbar.Snackbar.make(viewBinding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
		}
		viewModel.isBottomNavPinned.observe(this, ::setNavbarPinned)
		searchSuggestionViewModel.isIncognitoModeEnabled.observe(this, this::onIncognitoModeChanged)
		viewBinding.bottomNav?.addOnLayoutChangeListener(this)
		applyNavbarTintIfNeeded()
		viewBinding.searchView.addTransitionListener(this)
		viewBinding.searchView.addTransitionListener(exitCallback)
		initSearch()
		SearchEnhancer.attach(this, viewBinding, settings)
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		adjustSearchUI(viewBinding.searchView.isShowing)
		navigationDelegate.syncSelectedItem()
	}

	override fun onResume() {
		super.onResume()
		lifecycleScope.launch(Dispatchers.Default) {
			updatePluginsProvider.runAutoUpdate(settings)
		}
	}

	override fun onFragmentChanged(fragment: Fragment, fromUser: Boolean) {
		adjustFabVisibility(topFragment = fragment)
		adjustAppbar(topFragment = fragment)
		if (fromUser) {
			actionModeDelegate.finishActionMode()
			viewBinding.appbar.setExpanded(true)
		}
	}

	override fun addMenuProvider(provider: MenuProvider, owner: LifecycleOwner, state: Lifecycle.State) {
		if (provider !is MangaSearchMenuProvider) { // do not duplicate search menu item
			super.addMenuProvider(provider, owner, state)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.fab, R.id.railFab -> viewModel.openLastReader()
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val searchBarDefaultMargin = resources.getDimensionPixelOffset(materialR.dimen.m3_searchbar_margin_horizontal)
		viewBinding.searchBar.updateLayoutParams<MarginLayoutParams> {
			marginEnd = searchBarDefaultMargin + barsInsets.end(v)
			marginStart = if (viewBinding.navRail != null) {
				searchBarDefaultMargin
			} else {
				searchBarDefaultMargin + barsInsets.start(v)
			}
		}
		viewBinding.bottomNav?.updatePadding(
			left = barsInsets.left,
			right = barsInsets.right,
			bottom = barsInsets.bottom,
		)
		viewBinding.navRail?.updateLayoutParams<MarginLayoutParams> {
			marginStart = barsInsets.start(v)
			topMargin = barsInsets.top
			bottomMargin = barsInsets.bottom
		}
		updateContainerBottomMargin()
		return insets.consume(v, typeMask, start = viewBinding.navRail != null).also {
			handleSearchSuggestionsInsets(it)
		}
	}

	override fun onLayoutChange(
		v: View?,
		left: Int,
		top: Int,
		right: Int,
		bottom: Int,
		oldLeft: Int,
		oldTop: Int,
		oldRight: Int,
		oldBottom: Int
	) {
		if (top != oldTop || bottom != oldBottom) {
			updateContainerBottomMargin()
		}
	}

	override fun onStateChanged(
		searchView: SearchView,
		previousState: SearchView.TransitionState,
		newState: SearchView.TransitionState,
	) {
		val wasOpened = previousState >= SearchView.TransitionState.SHOWING
		val isOpened = newState >= SearchView.TransitionState.SHOWING
		if (isOpened != wasOpened) {
			adjustSearchUI(isOpened)
		}
	}

	override fun onRemoveQuery(query: String) {
		searchSuggestionViewModel.deleteQuery(query)
	}

	override fun onSupportActionModeStarted(mode: ActionMode) {
		super.onSupportActionModeStarted(mode)
		adjustFabVisibility()
		bottomNav?.hide()
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = true
		updateContainerBottomMargin()
	}

	override fun onSupportActionModeFinished(mode: ActionMode) {
		super.onSupportActionModeFinished(mode)
		adjustFabVisibility()
		bottomNav?.show()
		(viewBinding.layoutSearch ?: viewBinding.searchBar).isInvisible = false
		updateContainerBottomMargin()
	}

	private fun onOpenReader(manga: Manga) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView
		router.openReader(manga, fab)
	}

	private fun onFeedCounterChanged(counter: Int) {
		navigationDelegate.setCounter(NavItem.FEED, counter)
	}

	private fun onIncognitoModeChanged(isIncognito: Boolean) {
		var options = viewBinding.searchView.getEditText().imeOptions
		options = if (isIncognito) {
			options or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
		} else {
			options and EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
		}
		viewBinding.searchView.getEditText().imeOptions = options
		invalidateOptionsMenu()
	}

	private fun openIncognitoModePicker() {
		showIncognitoModePicker(this, settings.incognitoMode) { mode ->
			viewModel.setIncognitoMode(mode)
			onIncognitoModeSelected(mode)
		}
	}

	private fun onIncognitoModeSelected(mode: com.kosen.reader.core.prefs.IncognitoMode) {
		if (mode == com.kosen.reader.core.prefs.IncognitoMode.HIDDEN_HISTORY) {
			settings.isPrivateHistoryVolumeShortcutEnabled = true
			MaterialAlertDialogBuilder(this)
				.setTitle(R.string.private_history_volume_shortcut)
				.setMessage(R.string.private_history_volume_shortcut_instructions)
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}
	}

	private fun showUpdateAvailableDialog(version: AppVersion) {
		UpdateChangelogBottomSheet.newInstance(version)
			.show(supportFragmentManager, "update_changelog")
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		val fab = viewBinding.fab ?: viewBinding.navRail?.headerView ?: return
		fab.isEnabled = !isLoading
	}

	private fun onResumeEnabledChanged(isEnabled: Boolean) {
		adjustFabVisibility(isResumeEnabled = isEnabled)
	}

	private fun onFirstStart() = try {
		lifecycleScope.launch(Dispatchers.Main) { // not a default `Main.immediate` dispatcher
			withContext(Dispatchers.Default) {
				LocalStorageCleanupWorker.enqueue(applicationContext)
			}
			withResumed {
				MangaPrefetchService.prefetchLast(this@MainActivity)
				requestNotificationsPermission()
				startService(Intent(this@MainActivity, LocalIndexUpdateService::class.java))
				startService(Intent(this@MainActivity, PeriodicalBackupService::class.java))
				if (settings.isAdBlockEnabled) {
					startService(Intent(this@MainActivity, AdListUpdateService::class.java))
				}
			}
		}
	} catch (e: IllegalStateException) {
		e.printStackTraceDebug()
	}

	private fun adjustAppbar(topFragment: Fragment) {
		when (topFragment) {
			is FavouritesContainerFragment -> {
				viewBinding.appbar.fitsSystemWindows = true
				viewBinding.searchBar.isVisible = true
				fadingAppbarMediator.bind()
			}
			else -> {
				viewBinding.searchBar.isVisible = true
				viewBinding.appbar.fitsSystemWindows = false
				fadingAppbarMediator.unbind()
			}
		}
	}

	private fun adjustFabVisibility(
		isResumeEnabled: Boolean = viewModel.isResumeEnabled.value,
		topFragment: Fragment? = navigationDelegate.primaryFragment,
		isSearchOpened: Boolean = viewBinding.searchView.isShowing,
	) {
		navigationDelegate.navRailHeader?.railFab?.isVisible = isResumeEnabled
		val fab = viewBinding.fab ?: return
		if (isResumeEnabled && !actionModeDelegate.isActionModeStarted && !isSearchOpened &&
			(topFragment is HistoryListFragment || topFragment is HistoryHomeUiHost)
		) {
			if (!fab.isVisible) {
				fab.show()
			}
		} else {
			if (fab.isVisible) {
				fab.hide()
			}
		}
	}

	private fun adjustSearchUI(isOpened: Boolean) {
		val appBarScrollFlags = if (isOpened) {
			SCROLL_FLAG_NO_SCROLL
		} else {
			SCROLL_FLAG_SCROLL or SCROLL_FLAG_ENTER_ALWAYS or SCROLL_FLAG_SNAP
		}
		viewBinding.insetsHolder.updateLayoutParams<AppBarLayout.LayoutParams> {
			scrollFlags = appBarScrollFlags
		}
		adjustFabVisibility(isSearchOpened = isOpened)
		bottomNav?.showOrHide(!isOpened)
		updateContainerBottomMargin()
	}

	private fun requestNotificationsPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
				this,
				Manifest.permission.POST_NOTIFICATIONS,
			) != PERMISSION_GRANTED
		) {
			ActivityCompat.requestPermissions(
				this,
				arrayOf(Manifest.permission.POST_NOTIFICATIONS),
				1,
			)
		}
	}

	private fun handleSearchSuggestionsInsets(insets: WindowInsetsCompat) {
		val typeMask = WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		viewBinding.recyclerViewSearch.setPadding(barsInsets.left, 0, barsInsets.right, barsInsets.bottom)
	}

	private fun initSearch() {
		val listener = SearchSuggestionListenerImpl(router, viewBinding.searchView, searchSuggestionViewModel)
		val adapter = SearchSuggestionAdapter(listener)
		viewBinding.searchView.toolbar.addMenuProvider(
			SearchSuggestionMenuProvider(this, voiceInputLauncher, searchSuggestionViewModel),
		)
		viewBinding.searchView.editText.addTextChangedListener(listener)
		viewBinding.recyclerViewSearch.adapter = adapter
		viewBinding.searchView.editText.setOnEditorActionListener(listener)

		viewBinding.searchView.observeState()
			.map { it >= SearchView.TransitionState.SHOWING }
			.distinctUntilChanged()
			.flatMapLatest { isShowing ->
				if (isShowing) {
					searchSuggestionViewModel.suggestion
				} else {
					emptyFlow()
				}
			}.observe(this, adapter)
		searchSuggestionViewModel.onError.observeEvent(
			this,
			SnackbarErrorObserver(viewBinding.recyclerViewSearch, null),
		)
		ItemTouchHelper(SearchSuggestionItemCallback(this))
			.attachToRecyclerView(viewBinding.recyclerViewSearch)
	}

	private fun applyNavbarTintIfNeeded() {
		if (BuildConfig.UX_REDESIGN) {
			applyUxNavbarTint()
			return
		}
		applyAmoledNavbarTintIfNeeded()
	}

	private fun applyUxNavbarTint() {
		val bottomNav = viewBinding.bottomNav ?: return
		val primary = "#F5C518".toColorInt()
		bottomNav.setBackgroundColor(Color.BLACK)
		val itemTint = ColorStateList(
			arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
			intArrayOf(primary, "#99FFFFFF".toColorInt()),
		)
		bottomNav.itemTextColor = itemTint
		bottomNav.itemIconTintList = itemTint
		bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 48))
	}

	/**
	 * When the user picked the AMOLED dark theme, force the bottom-nav background to pure black
	 * (instead of the Material surface tint that bleeds gray at the bottom of the screen on AMOLED
	 * panels) and tint the items so a selection is still visible against the black background.
	 * Active item / icon use the theme's primary color; inactive items use a translucent white so
	 * they don't disappear entirely.
	 */
	private fun applyAmoledNavbarTintIfNeeded() {
		if (!isDarkAmoledTheme()) return
		if (!settings.isAmoledNavBarEnabled) return
		val bottomNav = viewBinding.bottomNav ?: return
		val primary = MaterialColors.getColor(bottomNav, appcompatR.attr.colorPrimary, Color.WHITE)
		bottomNav.setBackgroundColor(Color.BLACK)
		val itemTint = ColorStateList(
			arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
			intArrayOf(primary, "#99FFFFFF".toColorInt()),
		)
		bottomNav.itemTextColor = itemTint
		bottomNav.itemIconTintList = itemTint
		bottomNav.itemActiveIndicatorColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(primary, 38))
	}

	private fun setNavbarPinned(isPinned: Boolean) {
		val bottomNavBar = viewBinding.bottomNav
		bottomNavBar?.isPinned = isPinned
		for (view in viewBinding.appbar.children) {
			val lp = view.layoutParams as? AppBarLayout.LayoutParams ?: continue
			val scrollFlags = if (isPinned) {
				lp.scrollFlags and SCROLL_FLAG_SCROLL.inv()
			} else {
				lp.scrollFlags or SCROLL_FLAG_SCROLL
			}
			if (scrollFlags != lp.scrollFlags) {
				lp.scrollFlags = scrollFlags
				view.layoutParams = lp
			}
		}
		updateContainerBottomMargin()
	}

	private fun updateContainerBottomMargin() {
		val bottomNavBar = viewBinding.bottomNav ?: return
		val newMargin = if (bottomNavBar.isPinned && bottomNavBar.isShownOrShowing) bottomNavBar.height else 0
		with(viewBinding.container) {
			val params = layoutParams as MarginLayoutParams
			if (params.bottomMargin != newMargin) {
				params.bottomMargin = newMargin
				layoutParams = params
			}
		}
	}

	private fun SearchView.observeState() = callbackFlow {
		val listener = SearchView.TransitionListener { _, _, state ->
			trySendBlocking(state)
		}
		addTransitionListener(listener)
		awaitClose { removeTransitionListener(listener) }
	}
}
