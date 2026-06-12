package com.kosen.reader.history.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.activity.viewModels
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationRequest.Biometric
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.biometric.registerForAuthenticationResult
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.kosen.reader.R
import com.kosen.reader.core.ui.BaseActivity
import com.kosen.reader.core.ui.util.DefaultTextWatcher
import com.kosen.reader.core.util.ext.consumeAllSystemBarsInsets
import com.kosen.reader.core.util.ext.getDisplayMessage
import com.kosen.reader.core.util.ext.observe
import com.kosen.reader.core.util.ext.observeEvent
import com.kosen.reader.core.util.ext.systemBarsInsets
import com.kosen.reader.databinding.ActivityProtectBinding
import com.kosen.reader.history.domain.PrivateHistoryLockManager
import javax.inject.Inject
import com.google.android.material.R as materialR

@AndroidEntryPoint
class PrivateHistoryUnlockActivity :
	BaseActivity<ActivityProtectBinding>(),
	TextView.OnEditorActionListener,
	DefaultTextWatcher,
	View.OnClickListener,
	AuthenticationResultCallback {

	@Inject lateinit var lockManager: PrivateHistoryLockManager

	private val viewModel by viewModels<PrivateHistoryUnlockViewModel>()
	private var canUseBiometric = false
	private var authMode = MODE_UNLOCK
	private var deviceCredentialButtonId = View.NO_ID

	private val biometricPrompt = registerForAuthenticationResult(resultCallback = this)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
		setContentView(ActivityProtectBinding.inflate(layoutInflater))
		authMode = intent.getIntExtra(EXTRA_MODE, MODE_UNLOCK)

		viewBinding.textViewTitle.setText(R.string.private_history_lock_title)
		viewBinding.textViewSubtitle.setText(
			if (authMode == MODE_VERIFY) {
				R.string.private_history_verify_pin_subtitle
			} else {
				R.string.private_history_unlock_hint
			},
		)
		viewBinding.buttonNext.setText(R.string.unlock)
		viewBinding.editPassword.setOnEditorActionListener(this)
		viewBinding.editPassword.addTextChangedListener(this)
		viewBinding.buttonNext.setOnClickListener(this)
		viewBinding.buttonCancel.setOnClickListener(this)

		viewBinding.editPassword.inputType = if (viewModel.isNumericPassword) {
			EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
		} else {
			EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
		}

		if (authMode == MODE_VERIFY) {
			addDeviceCredentialButton()
		}

		viewModel.onError.observeEvent(this, this::onError)
		viewModel.isLoading.observe(this, this::onLoadingStateChanged)
		viewModel.onUnlockSuccess.observeEvent(this) {
			onAuthSuccess()
		}
		lifecycleScope.launch {
			withResumed {
				canUseBiometric = useFingerprint()
				updateEndIcon()
				if (!canUseBiometric) {
					viewBinding.editPassword.requestFocus()
				}
			}
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		val basePadding = resources.getDimensionPixelOffset(R.dimen.screen_padding)
		viewBinding.root.setPadding(
			barsInsets.left + basePadding,
			barsInsets.top + basePadding,
			barsInsets.right + basePadding,
			barsInsets.bottom + basePadding,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.button_next -> viewModel.tryUnlock(viewBinding.editPassword.text?.toString().orEmpty())
			R.id.button_cancel -> finishWithFailure()
			materialR.id.text_input_end_icon -> useFingerprint()
			else -> if (v.id == deviceCredentialButtonId) useDeviceCredential()
		}
	}

	override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
		return if (actionId == EditorInfo.IME_ACTION_DONE && viewBinding.buttonNext.isEnabled) {
			viewBinding.buttonNext.performClick()
			true
		} else {
			false
		}
	}

	override fun afterTextChanged(s: Editable?) {
		viewBinding.layoutPassword.error = null
		viewBinding.buttonNext.isEnabled = !s.isNullOrEmpty()
		updateEndIcon()
	}

	override fun onAuthResult(result: AuthenticationResult) {
		if (result.isSuccess()) {
			onAuthSuccess()
		}
	}

	private fun onAuthSuccess() {
		if (authMode == MODE_UNLOCK) {
			val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
			if (!sessionId.isNullOrEmpty()) {
				lockManager.markUnlocked(sessionId)
			}
		}
		setResult(RESULT_OK)
		finishAfterTransition()
	}

	private fun finishWithFailure() {
		setResult(RESULT_CANCELED)
		finishAfterTransition()
	}

	private fun onError(e: Throwable) {
		viewBinding.layoutPassword.error = e.getDisplayMessage(resources)
	}

	private fun onLoadingStateChanged(isLoading: Boolean) {
		viewBinding.layoutPassword.isEnabled = !isLoading
	}

	private fun useFingerprint(): Boolean {
		if (!viewModel.isBiometricEnabled) {
			return false
		}
		if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK) != BIOMETRIC_SUCCESS) {
			return false
		}
		val request = AuthenticationRequest.biometricRequest(
			title = getString(R.string.private_history_lock_title),
			authFallback = Biometric.Fallback.NegativeButton(getString(android.R.string.cancel)),
			init = {
				setMinStrength(Biometric.Strength.Class2)
				setIsConfirmationRequired(false)
			},
		)
		biometricPrompt.launch(request)
		return true
	}

	private fun useDeviceCredential() {
		val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
		if (BiometricManager.from(this).canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
			return
		}
		val request = AuthenticationRequest.biometricRequest(
			title = getString(R.string.private_history_device_auth_title),
			authFallback = Biometric.Fallback.DeviceCredential,
			init = {
				setMinStrength(Biometric.Strength.Class2)
				setIsConfirmationRequired(false)
			},
		)
		biometricPrompt.launch(request)
	}

	private fun addDeviceCredentialButton() {
		val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
		if (BiometricManager.from(this).canAuthenticate(authenticators) != BIOMETRIC_SUCCESS) {
			return
		}
		val button = MaterialButton(
			this,
			null,
			com.google.android.material.R.attr.materialButtonOutlinedStyle,
		).apply {
			deviceCredentialButtonId = View.generateViewId()
			id = deviceCredentialButtonId
			setText(R.string.private_history_use_device_credential)
			setOnClickListener(this@PrivateHistoryUnlockActivity)
		}
		val params = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
			androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
			androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT,
		).apply {
			topToBottom = R.id.layout_password
			startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
			endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
			topMargin = resources.getDimensionPixelOffset(R.dimen.grid_spacing)
		}
		(viewBinding.root as androidx.constraintlayout.widget.ConstraintLayout).addView(button, params)
	}

	private fun updateEndIcon() = with(viewBinding.layoutPassword) {
		val isFingerprintIcon = canUseBiometric && viewBinding.editPassword.text.isNullOrEmpty()
		if (isFingerprintIcon == (endIconMode == TextInputLayout.END_ICON_CUSTOM)) {
			return@with
		}
		if (isFingerprintIcon) {
			endIconMode = TextInputLayout.END_ICON_CUSTOM
			setEndIconDrawable(androidx.biometric.R.drawable.fingerprint_dialog_fp_icon)
			endIconContentDescription = getString(androidx.biometric.R.string.use_biometric_label)
			setEndIconOnClickListener(this@PrivateHistoryUnlockActivity)
		} else {
			setEndIconOnClickListener(null)
			setEndIconDrawable(0)
			endIconContentDescription = null
			endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
		}
	}

	companion object {

		const val EXTRA_SESSION_ID = "session_id"
		const val EXTRA_MODE = "mode"
		const val MODE_UNLOCK = 0
		const val MODE_VERIFY = 1

		fun newUnlockIntent(context: Context, sessionId: String): Intent =
			Intent(context, PrivateHistoryUnlockActivity::class.java)
				.putExtra(EXTRA_SESSION_ID, sessionId)
				.putExtra(EXTRA_MODE, MODE_UNLOCK)

		fun newVerifyIntent(context: Context): Intent =
			Intent(context, PrivateHistoryUnlockActivity::class.java)
				.putExtra(EXTRA_MODE, MODE_VERIFY)
	}
}
