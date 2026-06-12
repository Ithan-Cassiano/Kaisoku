package com.kosen.reader.core.github

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class ApkUpdateCompatibility {
	COMPATIBLE,
	SIGNATURE_MISMATCH,
	PACKAGE_MIGRATION,
	VERSION_DOWNGRADE,
	INVALID_APK,
}

object ApkUpdateCompatibilityChecker {

	private val devPackageMigrations = mapOf(
		"com.kosen.reader.debug" to setOf("com.kosen.reader.dev"),
	)

	fun check(context: Context, apkFile: File): ApkUpdateCompatibility {
		if (!apkFile.isFile || !apkFile.canRead()) {
			return ApkUpdateCompatibility.INVALID_APK
		}
		val pm = context.packageManager
		val archiveInfo = getPackageArchiveInfo(pm, apkFile) ?: return ApkUpdateCompatibility.INVALID_APK
		val archivePackage = archiveInfo.packageName ?: return ApkUpdateCompatibility.INVALID_APK
		val installedPackage = context.packageName
		if (archivePackage != installedPackage) {
			val allowedTargets = devPackageMigrations[installedPackage]
			return if (allowedTargets != null && archivePackage in allowedTargets) {
				ApkUpdateCompatibility.PACKAGE_MIGRATION
			} else {
				ApkUpdateCompatibility.INVALID_APK
			}
		}
		val installedInfo = getInstalledPackageInfo(pm, archivePackage) ?: return ApkUpdateCompatibility.COMPATIBLE
		val archiveVersionCode = archiveInfo.versionCodeLong()
		val installedVersionCode = installedInfo.versionCodeLong()
		if (archiveVersionCode in 1..<installedVersionCode) {
			return ApkUpdateCompatibility.VERSION_DOWNGRADE
		}
		val archiveSignatures = getSignatures(archiveInfo).orEmpty()
		val installedSignatures = getSignatures(installedInfo).orEmpty()
		if (archiveSignatures.isEmpty() || installedSignatures.isEmpty()) {
			return ApkUpdateCompatibility.COMPATIBLE
		}
		return if (archiveSignatures.any { it in installedSignatures }) {
			ApkUpdateCompatibility.COMPATIBLE
		} else {
			ApkUpdateCompatibility.SIGNATURE_MISMATCH
		}
	}

	private fun getInstalledPackageInfo(pm: PackageManager, packageName: String): PackageInfo? = try {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(packageQueryFlags.toLong()))
		} else {
			@Suppress("DEPRECATION")
			pm.getPackageInfo(packageName, packageQueryFlags)
		}
	} catch (_: PackageManager.NameNotFoundException) {
		null
	}

	private fun getPackageArchiveInfo(pm: PackageManager, apkFile: File): PackageInfo? {
		val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(packageQueryFlags.toLong()))
		} else {
			@Suppress("DEPRECATION")
			pm.getPackageArchiveInfo(apkFile.absolutePath, packageQueryFlags)
		}
		return info?.also { pkgInfo ->
			pkgInfo.applicationInfo?.fixBasePaths(apkFile.absolutePath)
		}
	}

	private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
		val rawSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val signingInfo = pkgInfo.signingInfo ?: return null
			if (signingInfo.hasMultipleSigners()) {
				signingInfo.apkContentsSigners
			} else {
				signingInfo.signingCertificateHistory
			}
		} else {
			@Suppress("DEPRECATION")
			pkgInfo.signatures
		}
		return rawSignatures?.map { it.sha256Fingerprint() }
	}

	private fun ApplicationInfo.fixBasePaths(apkPath: String) {
		if (sourceDir == null) {
			sourceDir = apkPath
		}
		if (publicSourceDir == null) {
			publicSourceDir = apkPath
		}
	}

	private fun Signature.sha256Fingerprint(): String = MessageDigest.getInstance("SHA-256")
		.digest(toByteArray())
		.joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte) }

	@Suppress("DEPRECATION")
	private val packageQueryFlags = PackageManager.GET_SIGNING_CERTIFICATES or
		(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 0 else PackageManager.GET_SIGNATURES)

	private fun PackageInfo.versionCodeLong(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		longVersionCode
	} else {
		@Suppress("DEPRECATION")
		versionCode.toLong()
	}
}
