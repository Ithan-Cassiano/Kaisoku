package com.kosen.reader.reader.ui.pager.webtoon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.kosen.reader.core.exceptions.resolve.ExceptionResolver
import com.kosen.reader.core.os.NetworkState
import com.kosen.reader.databinding.ItemPageWebtoonBinding
import com.kosen.reader.reader.domain.PageLoader
import com.kosen.reader.reader.ui.config.ReaderSettings
import com.kosen.reader.reader.ui.pager.BaseReaderAdapter

class WebtoonAdapter(
	private val lifecycleOwner: LifecycleOwner,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : BaseReaderAdapter<WebtoonHolder>(loader, readerSettingsProducer, networkState, exceptionResolver) {

	override fun onCreateViewHolder(
		parent: ViewGroup,
		loader: PageLoader,
		readerSettingsProducer: ReaderSettings.Producer,
		networkState: NetworkState,
		exceptionResolver: ExceptionResolver,
	) = WebtoonHolder(
		owner = lifecycleOwner,
		binding = ItemPageWebtoonBinding.inflate(
			LayoutInflater.from(parent.context),
			parent,
			false,
		),
		loader = loader,
		readerSettingsProducer = readerSettingsProducer,
		networkState = networkState,
		exceptionResolver = exceptionResolver,
	)
}
