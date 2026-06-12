package com.kosen.reader.list.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.kosen.reader.R
import com.kosen.reader.core.db.entity.toEntity
import com.kosen.reader.core.model.FavouriteCategory
import com.kosen.reader.core.model.LocalMangaSource
import com.kosen.reader.core.model.titleResId
import com.kosen.reader.core.model.unwrap
import com.kosen.reader.core.parser.external.ExternalMangaSource
import com.kosen.reader.core.parser.favicon.faviconUri
import com.kosen.reader.parsers.model.ContentType as MangaContentType
import com.kosen.reader.parsers.model.MangaParserSource
import com.kosen.reader.parsers.model.MangaSource
import com.kosen.reader.parsers.model.MangaTag

sealed interface ListFilterOption {

	@get:StringRes
	val titleResId: Int

	@get:DrawableRes
	val iconResId: Int

	val titleText: CharSequence?

	val groupKey: String

	fun getIconData(): Any? = null

	data object Downloaded : ListFilterOption {

		override val titleResId: Int
			get() = R.string.on_device

		override val iconResId: Int
			get() = R.drawable.ic_storage

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_downloaded"
	}

	enum class Macro(
		@StringRes override val titleResId: Int,
		@DrawableRes override val iconResId: Int,
	) : ListFilterOption {

		UNREAD(R.string.unread, R.drawable.ic_bookmark),
		COMPLETED(R.string.status_completed, R.drawable.ic_state_finished),
		READING(R.string.status_reading, R.drawable.ic_state_ongoing),
		NEW_CHAPTERS(R.string.new_chapters, R.drawable.ic_updated),
		FAVORITE(R.string.favourites, R.drawable.ic_heart_outline),
		PRIVATE_HISTORY(R.string.private_history_filter, 0),
		WEEKLY_READ(R.string.history_weekly_filter, R.drawable.ic_updated),
		NSFW(R.string.nsfw, R.drawable.ic_nsfw),
		;

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = name
	}

	data class Branch(
		override val titleText: String?,
		val chaptersCount: Int,
	) : ListFilterOption {

		override val titleResId: Int
			get() = if (titleText == null) R.string.system_default else 0

		override val iconResId: Int
			get() = R.drawable.ic_language

		override val groupKey: String
			get() = "_branch"
	}

	data class Tag(
		val tag: MangaTag
	) : ListFilterOption {

		val tagId: Long = tag.toEntity().id

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = R.drawable.ic_tag

		override val titleText: String
			get() = tag.title

		override val groupKey: String
			get() = "_tag"
	}

	data class TagTitle(
		override val titleText: String,
	) : ListFilterOption {

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = R.drawable.ic_tag

		override val groupKey: String
			get() = "_tagtitle"
	}

	data class ContentType(
		val contentType: MangaContentType,
	) : ListFilterOption {

		override val titleResId: Int
			get() = contentType.titleResId

		override val iconResId: Int
			get() = R.drawable.ic_manga_source

		override val titleText: CharSequence?
			get() = null

		override val groupKey: String
			get() = "_ctype"
	}

	data class Favorite(
		val category: FavouriteCategory
	) : ListFilterOption {

		override val titleResId: Int
			get() = 0

		override val iconResId: Int
			get() = R.drawable.ic_heart_outline

		override val titleText: String
			get() = category.title

		override val groupKey: String
			get() = "_favcat"
	}

	data class Source(
		val mangaSource: MangaSource
	) : ListFilterOption {
		override val titleResId: Int
			get() = when (mangaSource.unwrap()) {
				is ExternalMangaSource -> R.string.external_source
				LocalMangaSource -> R.string.local_storage
				else -> 0
			}

		override val iconResId: Int
			get() = R.drawable.ic_web

		override val titleText: CharSequence?
			get() = when (val source = mangaSource.unwrap()) {
				is MangaParserSource -> source.title
				else -> null
			}

		override val groupKey: String
			get() = "_source"

		override fun getIconData() = mangaSource.faviconUri()
	}

	data class Inverted(
		val option: ListFilterOption,
		override val iconResId: Int,
		override val titleResId: Int,
		override val titleText: CharSequence?,
	) : ListFilterOption {

		override val groupKey: String
			get() = when (val base = option) {
				is ContentType -> "_inv_ctype_${base.contentType.name}"
				is Tag -> "_inv_tag_${base.tagId}"
				is Source -> "_inv_source_${base.mangaSource.name}"
				else -> "_inv" + option.groupKey
			}
	}

	companion object {

		val SFW
			get() = Inverted(
				option = Macro.NSFW,
				iconResId = R.drawable.ic_sfw,
				titleResId = R.string.sfw,
				titleText = null,
			)

		val NOT_FAVORITE
			get() = Inverted(
				option = Macro.FAVORITE,
				iconResId = R.drawable.ic_heart_off,
				titleResId = R.string.not_in_favorites,
				titleText = null,
			)

		val NOT_DOWNLOADED
			get() = Inverted(
				option = Downloaded,
				iconResId = R.drawable.ic_storage,
				titleResId = R.string.not_on_device,
				titleText = null,
			)

		fun excludeContentType(contentType: MangaContentType): Inverted = Inverted(
			option = ContentType(contentType),
			iconResId = R.drawable.ic_manga_source,
			titleResId = 0,
			titleText = null,
		)

		fun excludeTag(tag: MangaTag): Inverted = Inverted(
			option = Tag(tag),
			iconResId = R.drawable.ic_tag,
			titleResId = 0,
			titleText = null,
		)

		fun excludeSource(source: MangaSource): Inverted = Inverted(
			option = Source(source),
			iconResId = R.drawable.ic_web,
			titleResId = 0,
			titleText = null,
		)
	}
}
