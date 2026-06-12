package com.kosen.reader.core.github

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppVersion(
	val id: Long,
	val name: String,
	val url: String,
	val apkSize: Long,
	val apkUrl: String,
	val description: String,
	val versionCode: Int = parseReleaseVersionCode(description),
) : Parcelable {

	@IgnoredOnParcel
	val versionId = VersionId(name)
}
