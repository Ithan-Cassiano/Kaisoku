package com.kosen.reader.core.cache

import com.kosen.reader.core.util.SynchronizedSieveCache
import com.kosen.reader.parsers.model.MangaSource
import java.util.concurrent.TimeUnit
import com.kosen.reader.core.cache.MemoryContentCache.Key as CacheKey

class ExpiringLruCache<T>(
	val maxSize: Int,
	private val lifetime: Long,
	private val timeUnit: TimeUnit,
) {

	private val cache = SynchronizedSieveCache<CacheKey, ExpiringValue<T>>(maxSize)

	operator fun get(key: CacheKey): T? {
		val value = cache[key] ?: return null
		if (value.isExpired) {
			cache.remove(key)
		}
		return value.get()
	}

	operator fun set(key: CacheKey, value: T) {
		val value = ExpiringValue(value, lifetime, timeUnit)
		cache.put(key, value)
	}

	fun clear() {
		cache.evictAll()
	}

	fun trimToSize(size: Int) {
		cache.trimToSize(size)
	}

	fun remove(key: CacheKey) {
		cache.remove(key)
	}

	fun removeAll(source: MangaSource) {
		cache.removeIf { key, _ -> key.source == source }
	}
}
