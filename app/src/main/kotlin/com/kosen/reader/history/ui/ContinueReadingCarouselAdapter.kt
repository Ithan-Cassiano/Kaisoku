package com.kosen.reader.history.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kosen.reader.databinding.ItemContinueReadingBinding
import com.kosen.reader.parsers.model.Manga

internal class ContinueReadingCarouselAdapter(
	private val onClick: (Manga) -> Unit,
	private val onLongClick: (Manga) -> Unit,
	private val canHide: (Manga) -> Boolean,
) : RecyclerView.Adapter<ContinueReadingCarouselAdapter.ViewHolder>() {

	private var items: List<ContinueReadingItem> = emptyList()

	fun submit(items: List<ContinueReadingItem>) {
		this.items = items
		notifyDataSetChanged()
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val binding = ItemContinueReadingBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false,
		)
		return ViewHolder(binding, onClick, onLongClick, canHide)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(items[position])
	}

	override fun getItemCount(): Int = items.size

	class ViewHolder(
		private val binding: ItemContinueReadingBinding,
		private val onClick: (Manga) -> Unit,
		private val onLongClick: (Manga) -> Unit,
		private val canHide: (Manga) -> Boolean,
	) : RecyclerView.ViewHolder(binding.root) {

		fun bind(item: ContinueReadingItem) {
			val manga = item.manga
			binding.textViewTitle.text = manga.title
			binding.imageViewCover.setImageAsync(manga.coverUrl, manga.source)
			binding.progressView.setProgress(item.progress, animate = false)
			binding.root.setOnClickListener { onClick(manga) }
			binding.root.setOnLongClickListener {
				if (canHide(manga)) {
					onLongClick(manga)
					true
				} else {
					false
				}
			}
		}
	}
}
