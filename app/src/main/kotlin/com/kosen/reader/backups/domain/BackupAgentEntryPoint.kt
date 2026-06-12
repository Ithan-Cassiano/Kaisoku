package com.kosen.reader.backups.domain

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.kosen.reader.core.parser.mihon.MihonExtensionManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupAgentEntryPoint {
	val mihonExtensionManager: MihonExtensionManager
}
