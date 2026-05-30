package org.koitharu.kotatsu.core.os

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import org.koitharu.kotatsu.parsers.util.suspendlazy.suspendLazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppValidator @Inject constructor(
	@ApplicationContext private val context: Context,
) {
	val isOriginalApp = suspendLazy(Dispatchers.Default) {
		context.packageName.isNotBlank()
	}
}
