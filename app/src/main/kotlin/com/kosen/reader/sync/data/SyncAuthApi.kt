package com.kosen.reader.sync.data

import dagger.Reusable
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.kosen.reader.core.exceptions.SyncApiException
import com.kosen.reader.core.network.BaseHttpClient
import com.kosen.reader.core.util.ext.toRequestBody
import com.kosen.reader.parsers.util.await
import com.kosen.reader.parsers.util.parseJson
import com.kosen.reader.parsers.util.parseRaw
import com.kosen.reader.parsers.util.removeSurrounding
import javax.inject.Inject

@Reusable
class SyncAuthApi @Inject constructor(
	@BaseHttpClient private val okHttpClient: OkHttpClient,
) {

	suspend fun authenticate(syncURL: String, email: String, password: String): String {
		val body = JSONObject(
			mapOf("email" to email, "password" to password),
		).toRequestBody()
		val request = Request.Builder()
			.url("$syncURL/auth")
			.post(body)
			.build()
		val response = okHttpClient.newCall(request).await()
		if (response.isSuccessful) {
			return response.parseJson().getString("token")
		} else {
			val code = response.code
			val message = response.parseRaw().removeSurrounding('"')
			throw SyncApiException(message, code)
		}
	}

	suspend fun forgotPassword(syncURL: String, email: String) {
		val body = JSONObject(
			mapOf("email" to email),
		).toRequestBody()
		val request = Request.Builder()
			.url("$syncURL/forgot-password")
			.post(body)
			.build()
		val response = okHttpClient.newCall(request).await()
		if (!response.isSuccessful) {
			val code = response.code
			val message = response.parseRaw().removeSurrounding('"')
			throw SyncApiException(message, code)
		}
	}

	suspend fun resetPassword(syncURL: String, resetToken: String, password: String) {
		val body = JSONObject(
			mapOf("reset_token" to resetToken, "password" to password),
		).toRequestBody()
		val request = Request.Builder()
			.url("$syncURL/reset-password")
			.post(body)
			.build()
		val response = okHttpClient.newCall(request).await()
		if (!response.isSuccessful) {
			val code = response.code
			val message = response.parseRaw().removeSurrounding('"')
			throw SyncApiException(message, code)
		}
	}
}
