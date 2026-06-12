package com.kosen.reader.core.db.entity

import com.kosen.reader.core.model.isAdultContent
import com.kosen.reader.core.model.MangaSource
import com.kosen.reader.parsers.model.ContentRating
import com.kosen.reader.parsers.model.Manga
import com.kosen.reader.parsers.model.MangaChapter
import com.kosen.reader.parsers.model.MangaState
import com.kosen.reader.parsers.model.MangaTag
import com.kosen.reader.parsers.model.SortOrder
import com.kosen.reader.parsers.util.longHashCode
import com.kosen.reader.parsers.util.mapToSet
import com.kosen.reader.parsers.util.nullIfEmpty
import com.kosen.reader.parsers.util.toArraySet
import com.kosen.reader.parsers.util.toTitleCase

private const val VALUES_DIVIDER = '\n'

// Entity to model

fun TagEntity.toMangaTag() = MangaTag(
	key = this.key,
	title = this.title.toTitleCase(),
	source = MangaSource(this.source),
)

fun Collection<TagEntity>.toMangaTags() = mapToSet(TagEntity::toMangaTag)

fun Collection<TagEntity>.toMangaTagsList() = map(TagEntity::toMangaTag)

fun MangaEntity.toManga(tags: Set<MangaTag>, chapters: List<ChapterEntity>?) = Manga(
	id = this.id,
	title = this.title,
	altTitles = this.altTitles?.split(VALUES_DIVIDER)?.toArraySet().orEmpty(),
	state = this.state?.let { MangaState(it) },
	rating = this.rating,
	contentRating = ContentRating(this.contentRating)
		?: if (isNsfw) ContentRating.ADULT else null,
	url = this.url,
	publicUrl = this.publicUrl,
	coverUrl = this.coverUrl,
	largeCoverUrl = this.largeCoverUrl,
	authors = this.authors?.split(VALUES_DIVIDER)?.toArraySet().orEmpty(),
	source = MangaSource(this.source),
	tags = tags,
	chapters = chapters?.toMangaChapters(),
)

fun MangaWithTags.toManga(chapters: List<ChapterEntity>? = null) = manga.toManga(tags.toMangaTags(), chapters)

fun Collection<MangaWithTags>.toMangaList() = map { it.toManga() }

fun ChapterEntity.toMangaChapter() = MangaChapter(
	id = chapterId,
	title = title.nullIfEmpty(),
	number = number,
	volume = volume,
	url = url,
	scanlator = scanlator,
	uploadDate = uploadDate,
	branch = branch,
	source = MangaSource(source),
)

fun Collection<ChapterEntity>.toMangaChapters() = map { it.toMangaChapter() }

// Model to entity

fun Manga.toEntity() = MangaEntity(
	id = id,
	url = url,
	publicUrl = publicUrl,
	source = source.name,
	largeCoverUrl = largeCoverUrl,
	coverUrl = coverUrl.orEmpty(),
	altTitles = altTitles.joinToString(VALUES_DIVIDER.toString()),
	rating = rating,
	isNsfw = isAdultContent(),
	contentRating = contentRating?.name ?: if (isAdultContent()) ContentRating.ADULT.name else null,
	state = state?.name,
	title = title,
	authors = authors.joinToString(VALUES_DIVIDER.toString()),
)

fun MangaTag.toEntity() = TagEntity(
	title = title,
	key = key,
	source = source.name,
	id = "${key}_${source.name}".longHashCode(),
	isPinned = false, // for future use
)

fun Collection<MangaTag>.toEntities() = map(MangaTag::toEntity)

fun Iterable<IndexedValue<MangaChapter>>.toEntities(mangaId: Long) = map { (index, chapter) ->
	ChapterEntity(
		chapterId = chapter.id,
		mangaId = mangaId,
		title = chapter.title.orEmpty(),
		number = chapter.number,
		volume = chapter.volume,
		url = chapter.url,
		scanlator = chapter.scanlator,
		uploadDate = chapter.uploadDate,
		branch = chapter.branch,
		source = chapter.source.name,
		index = index,
	)
}

// Other

fun SortOrder(name: String, fallback: SortOrder): SortOrder = runCatching {
	SortOrder.valueOf(name)
}.getOrDefault(fallback)

fun MangaState(name: String): MangaState? = runCatching {
	MangaState.valueOf(name)
}.getOrNull()

fun ContentRating(name: String?): ContentRating? = runCatching {
	ContentRating.valueOf(name ?: return@runCatching null)
}.getOrNull()
