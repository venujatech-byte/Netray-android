package com.venujatech.netray.updates

import org.json.JSONArray
import org.json.JSONObject

data class AndroidRelease(
    val version: String,
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val assetName: String,
    val downloadUrl: String,
    val assetSize: Long,
    val checksumUrl: String?,
    val digest: String?
) {
    fun toJson(): String = JSONObject()
        .put("version", version).put("tag", tag).put("title", title).put("notes", notes)
        .put("publishedAt", publishedAt).put("assetName", assetName).put("downloadUrl", downloadUrl)
        .put("assetSize", assetSize).put("checksumUrl", checksumUrl).put("digest", digest)
        .toString()

    companion object {
        fun fromJson(json: String): AndroidRelease {
            val obj = JSONObject(json)
            return AndroidRelease(
                obj.getString("version"), obj.getString("tag"), obj.getString("title"), obj.optString("notes"),
                obj.optString("publishedAt"), obj.getString("assetName"), obj.getString("downloadUrl"),
                obj.optLong("assetSize"), obj.optString("checksumUrl").takeIf(String::isNotBlank),
                obj.optString("digest").takeIf(String::isNotBlank)
            )
        }
    }
}

sealed interface UpdateCheckResult {
    data class Available(val release: AndroidRelease) : UpdateCheckResult
    data class Current(val version: String) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object AndroidReleaseParser {
    fun newestCompatibleRelease(json: String, installedVersion: String, supportedAbis: List<String>): AndroidRelease? {
        val releases = JSONArray(json)
        var newestMatching: AndroidRelease? = null
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val tag = release.optString("tag_name")
            val version = normalizeTagVersion(tag) ?: continue
            val asset = selectApk(release.optJSONArray("assets") ?: JSONArray(), supportedAbis) ?: continue
            val checksumAsset = (0 until (release.optJSONArray("assets")?.length() ?: 0))
                .mapNotNull { release.optJSONArray("assets")?.optJSONObject(it) }
                .firstOrNull { it.optString("name").equals("SHA256SUMS.txt", ignoreCase = true) }
            val candidate = AndroidRelease(
                version = version,
                tag = tag,
                title = release.optString("name").ifBlank { "Netray Android $version" },
                notes = release.optString("body"),
                publishedAt = release.optString("published_at"),
                assetName = asset.optString("name"),
                downloadUrl = asset.optString("browser_download_url"),
                assetSize = asset.optLong("size"),
                checksumUrl = checksumAsset?.optString("browser_download_url")?.takeIf(String::isNotBlank),
                digest = asset.optString("digest").takeIf(String::isNotBlank)
            )
            if (VersionSemver.isNewer(candidate.version, installedVersion) &&
                (newestMatching == null || VersionSemver.compare(candidate.version, newestMatching.version) > 0)) {
                newestMatching = candidate
            }
        }
        return newestMatching
    }

    fun hasCompatibleAndroidAsset(json: String, supportedAbis: List<String>): Boolean {
        val releases = JSONArray(json)
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            if (!release.optBoolean("draft") && !release.optBoolean("prerelease") &&
                selectApk(release.optJSONArray("assets") ?: JSONArray(), supportedAbis) != null) return true
        }
        return false
    }

    private fun selectApk(assets: JSONArray, supportedAbis: List<String>): JSONObject? {
        val candidates = (0 until assets.length()).mapNotNull(assets::optJSONObject)
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) && it.optString("browser_download_url").startsWith("https://") }
        for (abi in supportedAbis) {
            candidates.firstOrNull { it.optString("name").contains("-$abi.apk", ignoreCase = true) }?.let { return it }
        }
        return candidates.firstOrNull { it.optString("name").contains("universal", ignoreCase = true) }
    }

    private fun normalizeTagVersion(tag: String): String? {
        val normalized = tag.removePrefix("android-").removePrefix("Android-").removePrefix("v").removePrefix("V")
        return normalized.takeIf { VersionSemver.parse(it) != null }
    }
}

object VersionSemver {
    private val pattern = Regex("^(?:android-)?[vV]?([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")

    data class Version(val major: Long, val minor: Long, val patch: Long, val prerelease: List<String>?)

    fun parse(raw: String): Version? {
        val match = pattern.matchEntire(raw.trim()) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: return null
        val minor = match.groupValues[2].toLongOrNull() ?: return null
        val patch = match.groupValues[3].toLongOrNull() ?: return null
        val pre = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.')
        if (pre?.any { it.isEmpty() || !it.matches(Regex("[0-9A-Za-z-]+")) } == true) return null
        return Version(major, minor, patch, pre)
    }

    fun isNewer(remote: String, local: String): Boolean = compare(remote, local) > 0

    fun compare(first: String, second: String): Int {
        val a = requireNotNull(parse(first)) { "Invalid version: $first" }
        val b = requireNotNull(parse(second)) { "Invalid version: $second" }
        compareValuesBy(a, b, Version::major, Version::minor, Version::patch).takeIf { it != 0 }?.let { return it }
        if (a.prerelease == null && b.prerelease == null) return 0
        if (a.prerelease == null) return 1
        if (b.prerelease == null) return -1
        val count = minOf(a.prerelease.size, b.prerelease.size)
        for (i in 0 until count) {
            val left = a.prerelease[i]
            val right = b.prerelease[i]
            val leftNum = left.toLongOrNull()
            val rightNum = right.toLongOrNull()
            val result = when {
                leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                leftNum != null -> -1
                rightNum != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return a.prerelease.size.compareTo(b.prerelease.size)
    }
}
