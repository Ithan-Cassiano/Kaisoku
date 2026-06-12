package com.kosen.reader.core.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

fun <T> AppSettings.observeAsFlow(key: String, valueProducer: AppSettings.() -> T) = flow {
	var lastValue: T = valueProducer()
	emit(lastValue)
	observeChanges().collect {
		if (it == key) {
			val value = valueProducer()
			if (value != lastValue) {
				emit(value)
			}
			lastValue = value
		}
	}
}

fun <T> AppSettings.observeAsFlow(vararg keys: String, valueProducer: AppSettings.() -> T) = flow {
	var lastValue: T = valueProducer()
	emit(lastValue)
	observeChanges().collect {
		if (it == null || it in keys) {
			val value = valueProducer()
			if (value != lastValue) {
				emit(value)
			}
			lastValue = value
		}
	}
}

fun <T> AppSettings.observeAsStateFlow(
	scope: CoroutineScope,
	key: String,
	valueProducer: AppSettings.() -> T,
): StateFlow<T> = observeAsStateFlow(scope, keys = arrayOf(key), valueProducer = valueProducer)

fun <T> AppSettings.observeAsStateFlow(
	scope: CoroutineScope,
	vararg keys: String,
	valueProducer: AppSettings.() -> T,
): StateFlow<T> = observeChanges().transform {
	if (it == null || it in keys) {
		emit(valueProducer())
	}
}.stateIn(scope, SharingStarted.Eagerly, valueProducer())
