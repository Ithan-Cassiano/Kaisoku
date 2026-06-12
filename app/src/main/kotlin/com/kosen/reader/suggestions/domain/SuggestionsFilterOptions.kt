package com.kosen.reader.suggestions.domain

import android.content.Context
import com.kosen.reader.R
import com.kosen.reader.core.model.titleResId
import com.kosen.reader.list.domain.ListFilterOption
import com.kosen.reader.parsers.model.ContentType
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag

private val SUGGESTION_CONTENT_TYPES = listOf(
	ContentType.MANGA,
	ContentType.MANHWA,
	ContentType.MANHUA,
	ContentType.COMICS,
	ContentType.NOVEL,
	ContentType.ONE_SHOT,
	ContentType.DOUJINSHI,
)

fun buildSuggestionsNsfwFilterOptions(includeNsfw: Boolean): List<ListFilterOption> = buildList {
	if (includeNsfw) {
		add(ListFilterOption.Macro.NSFW)
		add(ListFilterOption.SFW)
	}
}

fun buildSuggestionsContentTypeFilterOptions(): List<ListFilterOption> = buildList {
	for (contentType in SUGGESTION_CONTENT_TYPES) {
		add(ListFilterOption.ContentType(contentType))
		add(ListFilterOption.excludeContentType(contentType))
	}
}

fun appendSuggestionsTagFilterOptions(
	destination: MutableList<ListFilterOption>,
	tags: List<MangaTag>,
) {
	for (tag in tags) {
		destination.add(ListFilterOption.Tag(tag))
		destination.add(ListFilterOption.excludeTag(tag))
	}
}

fun appendSuggestionsSourceFilterOptions(
	destination: MutableList<ListFilterOption>,
	sources: List<MangaSource>,
) {
	for (source in sources) {
		destination.add(ListFilterOption.Source(source))
		destination.add(ListFilterOption.excludeSource(source))
	}
}

fun getSuggestionsFilterChipTitle(context: Context, option: ListFilterOption): String = when (option) {
	ListFilterOption.Macro.NSFW -> context.getString(R.string.nsfw_only)
	ListFilterOption.SFW -> context.getString(R.string.nsfw_exclude)
	is ListFilterOption.ContentType -> context.getString(
		R.string.filter_only,
		context.getString(option.contentType.titleResId),
	)
	is ListFilterOption.Inverted -> when (val base = option.option) {
		is ListFilterOption.ContentType -> context.getString(
			R.string.filter_exclude,
			context.getString(base.contentType.titleResId),
		)
		is ListFilterOption.Tag -> context.getString(R.string.filter_exclude, base.tag.title)
		is ListFilterOption.Source -> {
			val name = base.titleText?.toString()
				?: context.getString(base.titleResId)
			context.getString(R.string.filter_exclude, name)
		}
		ListFilterOption.Macro.NSFW -> context.getString(R.string.nsfw_exclude)
		else -> base.titleText?.toString() ?: context.getString(base.titleResId)
	}
	is ListFilterOption.Tag -> context.getString(R.string.filter_only, option.tag.title)
	is ListFilterOption.Source -> {
		val name = option.titleText?.toString() ?: context.getString(option.titleResId)
		context.getString(R.string.filter_only, name)
	}
	else -> option.titleText?.toString() ?: context.getString(option.titleResId)
}

fun isSuggestionsExcludeOption(option: ListFilterOption): Boolean =
	option is ListFilterOption.Inverted && option.option is ListFilterOption.ContentType
