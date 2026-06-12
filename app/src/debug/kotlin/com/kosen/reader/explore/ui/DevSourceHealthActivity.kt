package com.kosen.reader.explore.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.kosen.reader.R
import com.kosen.reader.core.cache.MemoryContentCache
import com.kosen.reader.core.model.MangaSourceInfo
import com.kosen.reader.explore.data.MangaSourcesRepository
import com.kosen.reader.explore.domain.SourceHealthTracker
import com.kosen.reader.parsers.model.MangaSource
import javax.inject.Inject

@AndroidEntryPoint
class DevSourceHealthActivity : AppCompatActivity() {

	@Inject
	lateinit var sourcesRepository: MangaSourcesRepository

	@Inject
	lateinit var memoryContentCache: MemoryContentCache

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val recyclerView = RecyclerView(this).apply {
			layoutManager = LinearLayoutManager(this@DevSourceHealthActivity)
		}
		setContentView(recyclerView)
		title = getString(R.string.dev_source_health_title)
		lifecycleScope.launch {
			val sources = sourcesRepository.getEnabledSources()
			recyclerView.adapter = SourceHealthAdapter(sources) { source, action ->
				lifecycleScope.launch {
					when (action) {
						SourceHealthAction.CLEAR_CACHE -> {
							memoryContentCache.clear(source)
							SourceHealthTracker.recordSuccess(source)
						}
						SourceHealthAction.DISABLE -> {
							sourcesRepository.setSourcesEnabled(setOf(source), isEnabled = false)
						}
					}
				}
			}
		}
	}

	private enum class SourceHealthAction {
		CLEAR_CACHE,
		DISABLE,
	}

	private class SourceHealthAdapter(
		private val sources: List<MangaSource>,
		private val onAction: (MangaSource, SourceHealthAction) -> Unit,
	) : RecyclerView.Adapter<SourceHealthAdapter.ViewHolder>() {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val view = LayoutInflater.from(parent.context)
				.inflate(android.R.layout.simple_list_item_2, parent, false)
			return ViewHolder(view, onAction)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			holder.bind(sources[position])
		}

		override fun getItemCount(): Int = sources.size

		class ViewHolder(
			itemView: View,
			private val onAction: (MangaSource, SourceHealthAction) -> Unit,
		) : RecyclerView.ViewHolder(itemView) {

			private val title = itemView.findViewById<TextView>(android.R.id.text1)
			private val subtitle = itemView.findViewById<TextView>(android.R.id.text2)

			fun bind(source: MangaSource) {
				val context = itemView.context
				val info = MangaSourceInfo(source, isEnabled = true, isPinned = false)
				val status = SourceHealthTracker.getStatus(info)
				title.text = source.name
				subtitle.text = when (status) {
					SourceHealthTracker.Status.OK -> context.getString(R.string.source_status_ok)
					SourceHealthTracker.Status.UNSTABLE -> context.getString(R.string.source_status_unstable)
					SourceHealthTracker.Status.OFFLINE -> context.getString(R.string.source_status_offline)
				}
				itemView.setOnClickListener {
					MaterialAlertDialogBuilder(context)
						.setTitle(source.name)
						.setItems(
							arrayOf(
								context.getString(R.string.dev_source_clear_cache),
								context.getString(R.string.dev_source_disable),
							),
						) { _, which ->
							when (which) {
								0 -> onAction(source, SourceHealthAction.CLEAR_CACHE)
								1 -> onAction(source, SourceHealthAction.DISABLE)
							}
						}
						.show()
				}
			}
		}
	}
}
