package com.kosen.reader.main.ui

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

object DevBatteryOptimizer {

	fun install(application: Application) {
		ProcessLifecycleOwner.get().lifecycle.addObserver(CoilMemoryTrimObserver(application))
	}

	private class CoilMemoryTrimObserver(
		private val application: Application,
	) : DefaultLifecycleObserver {

		override fun onStop(owner: LifecycleOwner) {
			val coil = EntryPointAccessors.fromApplication(
				application,
				DevBatteryOptimizerEntryPoint::class.java,
			).imageLoader()
			coil.memoryCache?.clear()
		}
	}
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DevBatteryOptimizerEntryPoint {
	fun imageLoader(): ImageLoader
}
