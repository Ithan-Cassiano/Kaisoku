package com.kosen.reader.history.ui

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.kosen.reader.R
import com.kosen.reader.core.prefs.ProgressIndicatorMode
import com.kosen.reader.image.ui.CoverImageView
import com.kosen.reader.list.domain.ReadingProgress
import com.kosen.reader.parsers.model.Manga

/**
 * Binds the mockup-style hero "Continue Reading" card (Kosen UX layout only).
 */
internal class UxContinueReadingHeroBinder(
	private val root: View,
) {

	private val heroCard: View? = root.findViewById(R.id.continue_hero_card)
	private val heroCover: CoverImageView? = root.findViewById(R.id.continue_hero_cover) as? CoverImageView
	private val heroTitle: TextView? = root.findViewById(R.id.continue_hero_title)
	private val heroSubtitle: TextView? = root.findViewById(R.id.continue_hero_subtitle)
	private val heroProgress: ProgressBar? = root.findViewById(R.id.continue_hero_progress)
	private val heroProgressLabel: TextView? = root.findViewById(R.id.continue_hero_progress_label)
	private val heroButton: Button? = root.findViewById(R.id.button_continue_hero)

	fun bind(
		items: List<ContinueReadingItem>,
		onClick: (Manga) -> Unit,
		onLongClick: (Manga) -> Unit,
	) {
		val card = heroCard ?: return
		val first = items.firstOrNull()
		if (first == null) {
			card.isVisible = false
			return
		}
		val manga = first.manga
		card.isVisible = true
		heroCover?.setImageAsync(manga.coverUrl, manga.source)
		heroTitle?.text = manga.title
		heroSubtitle?.text = buildSubtitle(manga, first.progress)
		val percent = ((first.progress?.percent ?: 0f) * 100f).toInt().coerceIn(0, 100)
		heroProgress?.progress = percent
		heroProgressLabel?.text = formatProgressLabel(first.progress)
		heroButton?.setOnClickListener { onClick(manga) }
		card.setOnClickListener { onClick(manga) }
		card.setOnLongClickListener {
			onLongClick(manga)
			true
		}
	}

	private fun buildSubtitle(manga: Manga, progress: ReadingProgress?): String {
		val parts = mutableListOf<String>()
		if (progress != null && progress.totalChapters > 0) {
			parts.add("${progress.chapters}/${progress.totalChapters}")
		}
		manga.author?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
		return parts.joinToString(" • ")
	}

	private fun formatProgressLabel(progress: ReadingProgress?): String {
		if (progress == null) {
			return ""
		}
		val percentText = ReadingProgress.percentToString(progress.percent)
		val suffix = when (progress.mode) {
			ProgressIndicatorMode.CHAPTERS_LEFT,
			ProgressIndicatorMode.CHAPTERS_READ,
			-> {
				if (progress.totalChapters > 0) {
					root.context.getString(R.string.chapters_left)
				} else {
					null
				}
			}
			else -> root.context.getString(R.string.chapters_left)
		}
		return if (suffix != null) {
			root.context.getString(R.string.ux_hero_progress_pattern, percentText, suffix)
		} else {
			root.context.getString(R.string.percent_string_pattern, percentText)
		}
	}
}
