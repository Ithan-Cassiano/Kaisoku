package com.kosen.reader.settings.sources.catalog

import com.kosen.reader.parsers.model.ContentType

data class SourcesCatalogFilter(
	val types: Set<ContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
	val mihonMode: SourceCatalogFilterMode,
	val pluginMode: SourceCatalogFilterMode,
)

enum class SourceCatalogFilterMode {
	NONE,
	INCLUDE,
	EXCLUDE,
}
