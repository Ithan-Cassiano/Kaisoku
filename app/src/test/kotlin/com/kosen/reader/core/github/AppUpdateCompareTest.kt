package com.kosen.reader.core.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCompareTest {

	@Test
	fun nineSevenPatchUpdateByVersionCode() {
		val release = AppVersion(
			id = 1L,
			name = "9.7.21",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10001]\n[channel:release]",
			versionCode = 10001,
		)
		assertTrue(release.isNewerThan("9.7.20", 10000))
		assertTrue(release.isNewerThan("9.7.20", 2058))
	}

	@Test
	fun oneZeroLineUpdateFromOneZeroFive() {
		val release = AppVersion(
			id = 1L,
			name = "1.0.6",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10021]",
			versionCode = 10021,
		)
		assertTrue(release.isNewerThan("1.0.5", 10019))
	}

	@Test
	fun oneZeroLineUpdateFromOneZeroOneToLatest() {
		val latest = AppVersion(
			id = 1L,
			name = "1.0.7",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10022]\n[channel:release]",
			versionCode = 10022,
		)
		assertTrue(latest.isNewerThan("1.0.1", 10001))
	}

	@Test
	fun oneZeroLineIgnoresNineSevenEvenWithHigherVersionCode() {
		val nineSeven = AppVersion(
			id = 1L,
			name = "9.7.22",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10020]",
			versionCode = 10020,
		)
		assertFalse(nineSeven.isNewerThan("1.0.1", 10001))
	}

	@Test
	fun selectLatestUpdateFromOneZeroOne() {
		val available = mutableListOf(
			AppVersion(1L, "9.7.22", "", 0L, "", "[versionCode:10020]\n[channel:release]", 10020),
			AppVersion(2L, "1.0.3", "", 0L, "", "[versionCode:2059]\n[channel:release]", 2059),
			AppVersion(3L, "1.0.7", "", 0L, "", "[versionCode:10022]\n[channel:release]", 10022),
		)
		val latest = selectLatestUpdate("1.0.1", 10001, available, isDevChannel = false)
		assertTrue(latest?.name == "1.0.7")
	}

	@Test
	fun resolveDevVersionCodeFallback() {
		assertEquals(10018, resolveDevVersionCode("1.0.18", "Sem marcador"))
		assertEquals(10020, resolveDevVersionCode("1.0.20", "[versionCode:10020]\n[channel:dev]"))
	}

	@Test
	fun devDebugSuffixUpdateToLatest() {
		val latest = AppVersion(
			id = 1L,
			name = "1.0.20",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10020]\n[channel:dev]",
			versionCode = 10020,
		)
		assertTrue(latest.isNewerThan("1.0.18-debug", 10018))
		assertFalse(latest.isNewerThan("1.0.20-debug", 10020))
	}

	@Test
	fun selectLatestDevUpdateFromOldBuild() {
		val available = mutableListOf(
			AppVersion(1L, "1.0.18", "", 0L, "", "[versionCode:10018]\n[channel:dev]", 10018),
			AppVersion(2L, "1.0.20", "", 0L, "", "[versionCode:10020]\n[channel:dev]", 10020),
		)
		val latest = selectLatestUpdate("1.0.11-debug", 10011, available, isDevChannel = true)
		assertTrue(latest?.name == "1.0.20")
	}

	@Test
	fun sameVersionIsNotOffered() {
		val release = AppVersion(
			id = 1L,
			name = "9.7.20",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10000]",
			versionCode = 10000,
		)
		assertFalse(release.isNewerThan("9.7.20", 10000))
	}

	@Test
	fun filterKeepsOnlyNineSevenLine() {
		val available = mutableListOf(
			AppVersion(1L, "9.7.21", "", 0L, "", "[versionCode:10001]", 10001),
			AppVersion(2L, "1.0.5", "", 0L, "", "[versionCode:10019]", 10019),
		)
		filterRelevantUpdates("9.7.20", available, isDevChannel = false)
		assertTrue(available.all { it.name.startsWith("9.7.") })
	}

	@Test
	fun filterKeepsOnlyOneZeroLine() {
		val available = mutableListOf(
			AppVersion(1L, "9.7.22", "", 0L, "", "[versionCode:10020]", 10020),
			AppVersion(2L, "1.0.6", "", 0L, "", "[versionCode:10021]", 10021),
			AppVersion(3L, "1.1.0", "", 0L, "", "[versionCode:10028]", 10028),
		)
		filterRelevantUpdates("1.0.5", available, isDevChannel = false)
		assertTrue(available.all { it.name.startsWith("1.") && !it.name.startsWith("9.7.") })
	}

	@Test
	fun oneZeroLineUpgradesToOneOneZero() {
		val release = AppVersion(
			id = 1L,
			name = "1.1.0",
			url = "",
			apkSize = 0L,
			apkUrl = "",
			description = "[versionCode:10028]\n[channel:release]",
			versionCode = 10028,
		)
		assertTrue(release.isNewerThan("1.0.12", 10027))
	}

	@Test
	fun selectLatestUpdatePrefersOneOneZeroOverOneZeroTwelve() {
		val available = mutableListOf(
			AppVersion(1L, "1.0.12", "", 0L, "", "[versionCode:10027]\n[channel:release]", 10027),
			AppVersion(2L, "1.1.0", "", 0L, "", "[versionCode:10028]\n[channel:release]", 10028),
		)
		val latest = selectLatestUpdate("1.0.11", 10026, available, isDevChannel = false)
		assertEquals("1.1.0", latest?.name)
	}

	@Test
	fun devChannelIgnoresReleaseApk() {
		val release = AppVersion(
			id = 1L,
			name = "9.7.20",
			url = "",
			apkSize = 0L,
			apkUrl = "https://example.com/Kosen-v9.7.20.apk",
			description = "[versionCode:10000]\n[channel:release]",
			versionCode = 10000,
		)
		assertFalse(release.isDevChannelUpdate())
		assertTrue(release.isReleaseChannelUpdate())
	}

	@Test
	fun devChannelAcceptsDevApk() {
		val dev = AppVersion(
			id = 2L,
			name = "1.0.7",
			url = "",
			apkSize = 0L,
			apkUrl = "https://example.com/Kosen-Dev-1.0.7-debug.apk",
			description = "[versionCode:10007]\n[channel:dev]",
			versionCode = 10007,
		)
		assertTrue(dev.isDevChannelUpdate())
		assertFalse(dev.isReleaseChannelUpdate())
	}

	@Test
	fun comparePrefersHigherNineSevenVersion() {
		val newer = AppVersion(1L, "9.7.21", "", 0L, "", "[versionCode:10001]", 10001)
		val older = AppVersion(2L, "9.7.20", "", 0L, "", "[versionCode:10000]", 10000)
		assertTrue(compareAppVersions(newer, older) > 0)
	}

	@Test
	fun oneOneLineUpgradesToTwoZeroZero() {
		val release = AppVersion(
			id = 1L,
			name = "2.0.0",
			url = "",
			apkSize = 0L,
			apkUrl = "https://example.com/Kosen-v2.0.0.apk",
			description = "[versionCode:20000]\n[channel:release]",
			versionCode = 20000,
		)
		assertTrue(release.isNewerThan("1.1.18", 10046))
	}

	@Test
	fun selectLatestUpdateFromOneOneEightToTwoZeroZero() {
		val available = mutableListOf(
			AppVersion(1L, "1.1.18", "", 0L, "", "[versionCode:10046]\n[channel:release]", 10046),
			AppVersion(2L, "2.0.0", "", 0L, "", "[versionCode:20000]\n[channel:release]", 20000),
			AppVersion(3L, "9.7.22", "", 0L, "", "[versionCode:10020]\n[channel:release]", 10020),
		)
		val latest = selectLatestUpdate("1.1.18", 10046, available, isDevChannel = false)
		assertEquals("2.0.0", latest?.name)
	}

	@Test
	fun majorVersionUpgradePath() {
		val three = AppVersion(
			id = 1L,
			name = "3.0.0",
			url = "",
			apkSize = 0L,
			apkUrl = "https://example.com/Kosen-v3.0.0.apk",
			description = "[versionCode:30000]\n[channel:release]",
			versionCode = 30000,
		)
		val five = AppVersion(
			id = 2L,
			name = "5.0.0",
			url = "",
			apkSize = 0L,
			apkUrl = "https://example.com/Kosen-v5.0.0.apk",
			description = "[versionCode:50000]\n[channel:release]",
			versionCode = 50000,
		)
		assertTrue(three.isNewerThan("1.1.18", 10046))
		assertTrue(three.isNewerThan("2.0.1", 20001))
		assertTrue(five.isNewerThan("3.0.0", 30000))
		assertTrue(five.isNewerThan("4.0.0", 40000))
	}

	@Test
	fun selectLatestUpdatePicksHighestMajor() {
		val available = mutableListOf(
			AppVersion(1L, "2.0.1", "", 0L, "", "[versionCode:20001]\n[channel:release]", 20001),
			AppVersion(2L, "3.0.0", "", 0L, "", "[versionCode:30000]\n[channel:release]", 30000),
			AppVersion(3L, "5.0.0", "", 0L, "", "[versionCode:50000]\n[channel:release]", 50000),
			AppVersion(4L, "4.0.0", "", 0L, "", "[versionCode:40000]\n[channel:release]", 40000),
		)
		val latest = selectLatestUpdate("1.1.18", 10046, available, isDevChannel = false)
		assertEquals("5.0.0", latest?.name)
	}

	@Test
	fun resolveReleaseVersionCodeDerivesModernMajorWithoutMarker() {
		assertEquals(30000, resolveReleaseVersionCode("3.0.0", "Sem marcador"))
		assertEquals(40502, resolveReleaseVersionCode("4.5.2", "Sem marcador"))
		assertEquals(50000, resolveReleaseVersionCode("5.0.0", "[channel:release]"))
	}
}
