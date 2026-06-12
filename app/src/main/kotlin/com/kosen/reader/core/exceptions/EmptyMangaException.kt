package com.kosen.reader.core.exceptions

import com.kosen.reader.details.ui.pager.EmptyMangaReason
import com.kosen.reader.parsers.model.Manga

class EmptyMangaException(
    val reason: EmptyMangaReason?,
    val manga: Manga,
    cause: Throwable?
) : IllegalStateException(cause)
