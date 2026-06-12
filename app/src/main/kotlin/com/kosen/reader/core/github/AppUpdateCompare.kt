package com.kosen.reader.core.github

const val UPDATE_CHANNEL_DEV_MARKER = "[channel:dev]"
const val UPDATE_CHANNEL_RELEASE_MARKER = "[channel:release]"

private val VERSION_CODE_REGEX = Regex(
	"""(?:<!--\s*versionCode:(\d+)\s*-->|\[versionCode:(\d+)\])""",
)

fun parseReleaseVersionCode(description: String): Int =
	VERSION_CODE_REGEX.find(description)?.let { match ->
		match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toIntOrNull()
	} ?: 0

private val KNOWN_RELEASE_VERSION_CODES = mapOf(
	"1.0.0" to 10000,
	"1.0.1" to 10001,
	"1.0.2" to 10002,
	"1.0.3" to 2059,
	"1.0.4" to 2060,
	"1.0.5" to 10019,
	"1.0.6" to 10021,
	"1.0.7" to 10022,
	"1.0.8" to 10023,
	"1.0.9" to 10024,
	"1.0.10" to 10025,
	"1.0.11" to 10026,
	"1.0.12" to 10027,
	"1.1.0" to 10028,
	"1.1.1" to 10029,
	"2.0.0" to 20000,
	"2.0.1" to 20001,
)

fun normalizeUpdateVersionName(versionName: String): String =
	versionName.substringBeforeLast('-').trim()

fun resolveReleaseVersionCode(versionName: String, description: String): Int {
	val parsed = parseReleaseVersionCode(description)
	if (parsed > 0) {
		return parsed
	}
	val normalized = normalizeUpdateVersionName(versionName)
	return KNOWN_RELEASE_VERSION_CODES[normalized.removePrefix("v")]
		?: deriveModernReleaseVersionCode(VersionId(normalized))
}

fun resolveDevVersionCode(versionName: String, description: String): Int {
	val parsed = parseReleaseVersionCode(description)
	if (parsed > 0) {
		return parsed
	}
	val versionId = VersionId(normalizeUpdateVersionName(versionName))
	if (isOneZeroLine(versionId)) {
		return 10000 + versionId.build
	}
	return 0
}

fun resolveUpdateVersionCode(versionName: String, description: String, isDevChannel: Boolean): Int =
	if (isDevChannel) {
		resolveDevVersionCode(versionName, description)
	} else {
		resolveReleaseVersionCode(versionName, description)
	}

private fun isNineSevenLine(versionId: VersionId): Boolean =
	versionId.major == 9 && versionId.minor == 7

private fun isOneZeroLine(versionId: VersionId): Boolean =
	versionId.major == 1 && versionId.minor == 0

/** Fork Kotatsu 9.7.x — canal isolado, sem upgrade cruzado para Kosen. */
private fun isLegacyKotatsuLine(versionId: VersionId): Boolean =
	isNineSevenLine(versionId)

/**
 * Linha de release Kosen: qualquer major moderno (1.x, 2.x, 3.x, 4.x…).
 * Upgrades entre majors são permitidos (ex.: 1.1.18 → 3.0.0 → 5.0.0).
 */
private fun isKosenReleaseLine(versionId: VersionId): Boolean =
	!isLegacyKotatsuLine(versionId) && versionId.major >= 1

/** major * 10000 + minor * 100 + build — válido a partir do major 2. */
private fun deriveModernReleaseVersionCode(versionId: VersionId): Int {
	if (!isKosenReleaseLine(versionId) || versionId.major < 2) {
		return 0
	}
	return versionId.major * 10_000 + versionId.minor * 100 + versionId.build
}

private fun isSameReleaseLine(currentId: VersionId, candidateId: VersionId): Boolean =
	when {
		isNineSevenLine(currentId) -> isNineSevenLine(candidateId)
		isKosenReleaseLine(currentId) -> isKosenReleaseLine(candidateId)
		else -> currentId.major == candidateId.major && currentId.minor == candidateId.minor
	}

fun AppVersion.isNewerThan(currentVersionName: String, currentVersionCode: Int): Boolean {
	val normalizedCurrent = normalizeUpdateVersionName(currentVersionName)
	val normalizedRelease = normalizeUpdateVersionName(name)
	val currentId = VersionId(normalizedCurrent)
	val releaseId = VersionId(normalizedRelease)

	if (normalizedRelease == normalizedCurrent &&
		versionCode > 0 &&
		currentVersionCode > 0 &&
		versionCode <= currentVersionCode
	) {
		return false
	}

	if (isKosenReleaseLine(currentId) && isNineSevenLine(releaseId)) {
		return false
	}
	if (isNineSevenLine(currentId) && isKosenReleaseLine(releaseId)) {
		return false
	}

	if (isNineSevenLine(currentId) && isNineSevenLine(releaseId)) {
		return when {
			releaseId > currentId -> true
			releaseId < currentId -> false
			else -> versionCode > currentVersionCode && versionCode > 0 && currentVersionCode > 0
		}
	}

	if (isKosenReleaseLine(currentId) && isKosenReleaseLine(releaseId)) {
		return when {
			releaseId > currentId -> true
			releaseId < currentId -> false
			else -> versionCode > currentVersionCode && versionCode > 0 && currentVersionCode > 0
		}
	}

	if (versionCode > 0 && currentVersionCode > 0 && versionCode > currentVersionCode) {
		return true
	}
	return releaseId > currentId
}

fun compareAppVersions(a: AppVersion, b: AppVersion): Int {
	if (isNineSevenLine(a.versionId) && isNineSevenLine(b.versionId)) {
		val semver = a.versionId.compareTo(b.versionId)
		if (semver != 0) {
			return semver
		}
		if (a.versionCode > 0 && b.versionCode > 0) {
			return a.versionCode.compareTo(b.versionCode)
		}
		return 0
	}
	if (isKosenReleaseLine(a.versionId) && isKosenReleaseLine(b.versionId)) {
		val semver = a.versionId.compareTo(b.versionId)
		if (semver != 0) {
			return semver
		}
		if (a.versionCode > 0 && b.versionCode > 0) {
			return a.versionCode.compareTo(b.versionCode)
		}
		return 0
	}
	if (a.versionCode > 0 && b.versionCode > 0) {
		val diff = a.versionCode.compareTo(b.versionCode)
		if (diff != 0) {
			return diff
		}
	}
	return a.versionId.compareTo(b.versionId)
}

fun compareAppVersionsForUpdate(a: AppVersion, b: AppVersion, currentVersionName: String): Int {
	val currentId = VersionId(currentVersionName)
	val aSameLine = isSameReleaseLine(currentId, a.versionId)
	val bSameLine = isSameReleaseLine(currentId, b.versionId)
	if (aSameLine && !bSameLine) {
		return 1
	}
	if (!aSameLine && bSameLine) {
		return -1
	}
	return compareAppVersions(a, b)
}

fun selectLatestUpdate(
	currentVersionName: String,
	currentVersionCode: Int,
	available: MutableList<AppVersion>,
	isDevChannel: Boolean,
): AppVersion? {
	val normalizedCurrent = normalizeUpdateVersionName(currentVersionName)
	filterRelevantUpdates(normalizedCurrent, available, isDevChannel)
	val currentId = VersionId(normalizedCurrent)
	when {
		isKosenReleaseLine(currentId) -> available.retainAll { isKosenReleaseLine(it.versionId) }
		isNineSevenLine(currentId) -> available.retainAll { isNineSevenLine(it.versionId) }
	}
	available.retainAll { it.isNewerThan(normalizedCurrent, currentVersionCode) }
	return available.maxWithOrNull { a, b ->
		compareAppVersionsForUpdate(a, b, normalizedCurrent)
	}
}

fun filterRelevantUpdates(
	currentVersionName: String,
	available: MutableList<AppVersion>,
	isDevChannel: Boolean,
) {
	val normalizedCurrent = normalizeUpdateVersionName(currentVersionName)
	if (isDevChannel) {
		available.retainAll { version ->
			version.isDevChannelUpdate() ||
				!version.description.contains(UPDATE_CHANNEL_RELEASE_MARKER, ignoreCase = true)
		}
		return
	}
	available.retainAll { it.isReleaseChannelUpdate() }
	val currentId = VersionId(normalizedCurrent)
	when {
		isNineSevenLine(currentId) -> available.retainAll { isNineSevenLine(it.versionId) }
		isKosenReleaseLine(currentId) -> available.retainAll { isKosenReleaseLine(it.versionId) }
	}
}

fun AppVersion.isDevChannelUpdate(): Boolean {
	if (description.contains(UPDATE_CHANNEL_DEV_MARKER, ignoreCase = true)) {
		return true
	}
	if (description.contains(UPDATE_CHANNEL_RELEASE_MARKER, ignoreCase = true)) {
		return false
	}
	return apkUrl.contains("debug", ignoreCase = true) ||
		apkUrl.contains("-Dev-", ignoreCase = true) ||
		name.contains("debug", ignoreCase = true)
}

fun AppVersion.isReleaseChannelUpdate(): Boolean {
	if (description.contains(UPDATE_CHANNEL_DEV_MARKER, ignoreCase = true)) {
		return false
	}
	if (description.contains(UPDATE_CHANNEL_RELEASE_MARKER, ignoreCase = true)) {
		return true
	}
	if (isKosenReleaseLine(versionId)) {
		return true
	}
	if (isNineSevenLine(versionId)) {
		return false
	}
	return !apkUrl.contains("debug", ignoreCase = true) &&
		!apkUrl.contains("-Dev-", ignoreCase = true)
}

fun appendReleaseVersionCodeMarker(description: String, versionCode: Int): String {
	if (versionCode <= 0 || VERSION_CODE_REGEX.containsMatchIn(description)) {
		return description
	}
	return description.trimEnd() + "\n\n[versionCode:$versionCode]"
}

fun appendReleaseChannelMarker(description: String, isDevChannel: Boolean): String {
	val marker = if (isDevChannel) UPDATE_CHANNEL_DEV_MARKER else UPDATE_CHANNEL_RELEASE_MARKER
	if (description.contains(marker, ignoreCase = true)) {
		return description
	}
	return description.trimEnd() + "\n\n$marker"
}
