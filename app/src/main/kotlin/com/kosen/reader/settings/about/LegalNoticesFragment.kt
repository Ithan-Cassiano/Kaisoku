package com.kosen.reader.settings.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import com.kosen.reader.R
import com.kosen.reader.core.ui.BaseFragment
import com.kosen.reader.core.util.ext.container
import com.kosen.reader.core.util.ext.consumeAll
import com.kosen.reader.core.util.ext.end
import com.kosen.reader.core.util.ext.start
import com.kosen.reader.databinding.FragmentChangelogBinding
import java.io.InputStreamReader

class LegalNoticesFragment : BaseFragment<FragmentChangelogBinding>() {

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentChangelogBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentChangelogBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.progressBar.visibility = View.GONE
		binding.textViewContent.text = readLicensesText()
	}

	override fun onResume() {
		super.onResume()
		activity?.setTitle(R.string.legal_notices)
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat,
	): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		val barsInsets = insets.getInsets(typeMask)
		val isTablet = !resources.getBoolean(R.bool.is_tablet)
		val isMaster = container?.id == R.id.container_master
		val basePadding = resources.getDimensionPixelOffset(R.dimen.screen_padding)
		requireViewBinding().textViewContent.setPaddingRelative(
			basePadding + if (isTablet && !isMaster) 0 else barsInsets.start(v),
			basePadding,
			basePadding + if (isTablet && isMaster) 0 else barsInsets.end(v),
			basePadding + barsInsets.bottom,
		)
		return insets.consumeAll(typeMask)
	}

	private fun readLicensesText(): String {
		return requireContext().resources.openRawResource(R.raw.licenses_notice).use { stream ->
			InputStreamReader(stream, Charsets.UTF_8).readText()
		}
	}
}
