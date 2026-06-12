package com.kosen.reader.core.exceptions

import okio.IOException

class WrapperIOException(override val cause: Exception) : IOException(cause)
