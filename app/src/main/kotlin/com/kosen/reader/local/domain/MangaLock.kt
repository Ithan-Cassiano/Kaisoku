package com.kosen.reader.local.domain

import com.kosen.reader.core.util.MultiMutex
import com.kosen.reader.parsers.model.Manga
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaLock @Inject constructor() : MultiMutex<Manga>()
