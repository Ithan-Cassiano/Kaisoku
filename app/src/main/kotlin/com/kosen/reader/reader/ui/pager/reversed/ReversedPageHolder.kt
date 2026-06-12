package com.kosen.reader.reader.ui.pager.reversed

import android.graphics.PointF
import android.view.Gravity
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.kosen.reader.core.exceptions.resolve.ExceptionResolver
import com.kosen.reader.core.model.ZoomMode
import com.kosen.reader.core.os.NetworkState
import com.kosen.reader.databinding.ItemPageBinding
import com.kosen.reader.reader.domain.PageLoader
import com.kosen.reader.reader.ui.config.ReaderSettings
import com.kosen.reader.reader.ui.pager.standard.PageHolder

class ReversedPageHolder(
	owner: LifecycleOwner,
	binding: ItemPageBinding,
	loader: PageLoader,
	readerSettingsProducer: ReaderSettings.Producer,
	networkState: NetworkState,
	exceptionResolver: ExceptionResolver,
) : PageHolder(
	owner = owner,
	binding = binding,
	loader = loader,
	readerSettingsProducer = readerSettingsProducer,
	networkState = networkState,
	exceptionResolver = exceptionResolver,
) {

	init {
		(binding.textViewNumber.layoutParams as FrameLayout.LayoutParams)
			.gravity = Gravity.START or Gravity.BOTTOM
	}

	override fun onReady() {
		with(binding.ssiv) {
			maxScale = defaultMaxScale(this)
			binding.ssiv.colorFilter = settings.colorFilter?.toColorFilter()
			when (settings.zoomMode) {
				ZoomMode.FIT_CENTER -> {
					minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
					resetScaleAndCenter()
				}

				ZoomMode.FIT_HEIGHT -> {
					minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
					minScale = height / sHeight.toFloat()
					setScaleAndCenter(
						minScale,
						PointF(sWidth.toFloat(), sHeight / 2f),
					)
				}

				ZoomMode.FIT_WIDTH -> {
					minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CUSTOM
					minScale = width / sWidth.toFloat()
					setScaleAndCenter(
						minScale,
						PointF(sWidth / 2f, 0f),
					)
				}

				ZoomMode.KEEP_START -> {
					minimumScaleType = SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
					setScaleAndCenter(
						maxScale,
						PointF(sWidth.toFloat(), 0f),
					)
				}
			}
		}
	}
}
