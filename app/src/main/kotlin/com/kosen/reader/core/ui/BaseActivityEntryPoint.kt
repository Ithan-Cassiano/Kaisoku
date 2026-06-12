package com.kosen.reader.core.ui

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.kosen.reader.core.exceptions.resolve.ExceptionResolver
import com.kosen.reader.core.prefs.AppSettings
import com.kosen.reader.history.domain.PrivateHistoryVolumeShortcut

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BaseActivityEntryPoint {

	val settings: AppSettings

	val exceptionResolverFactory: ExceptionResolver.Factory

	val privateHistoryVolumeShortcut: PrivateHistoryVolumeShortcut
}
