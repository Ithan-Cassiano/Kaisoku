package com.kosen.reader.scrobbling.common.domain

import okio.IOException
import com.kosen.reader.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
