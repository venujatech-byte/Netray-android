package com.venujatech.netray.updates

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.venujatech.netray.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    fun isCheckDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis - preferences.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    fun hasNetwork(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    suspend fun check(installedVersion: String, force: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (!force && !isCheckDue()) {
            val cached = preferences.getString(KEY_RESULT, null)?.let { runCatching { AndroidRelease.fromJson(it) }.getOrNull() }
            return@withContext cached?.let { UpdateCheckResult.Available(it) } ?: UpdateCheckResult.Current(installedVersion)
        }
        try {
            val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases?per_page=30"
            val cachedBody = preferences.getString(KEY_BODY, null)
            val response = request(url, preferences.getString(KEY_ETAG, null))
            val body = when (response.code) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> cachedBody ?: throw IOException("GitHub returned 304 without cached release data")
                HttpURLConnection.HTTP_OK -> response.body
                403, 429 -> {
                    val retryAfter = response.headers["Retry-After"]
                    throw IOException(if (retryAfter.isNullOrBlank()) "GitHub rate limit reached; try again later" else "GitHub rate limit reached; retry after $retryAfter seconds")
                }
                else -> throw IOException("GitHub release check failed (HTTP ${response.code})")
            }
            if (response.code == HttpURLConnection.HTTP_OK) {
                if (body.length > MAX_API_BODY) throw IOException("GitHub release response is too large")
                preferences.edit()
                    .putString(KEY_BODY, body)
                    .putString(KEY_ETAG, response.headers["ETag"])
                    .apply()
            }
            val supportedAbis = android.os.Build.SUPPORTED_ABIS?.toList().orEmpty()
            val release = AndroidReleaseParser.newestCompatibleRelease(body, installedVersion, supportedAbis)
            preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (release == null) {
                val hasAndroidAsset = AndroidReleaseParser.hasCompatibleAndroidAsset(body, supportedAbis)
                if (!hasAndroidAsset) throw IOException("No compatible Android APK was found in recent releases")
                preferences.edit().remove(KEY_RESULT).apply()
                UpdateCheckResult.Current(installedVersion)
            } else {
                preferences.edit().putString(KEY_RESULT, release.toJson()).apply()
                UpdateCheckResult.Available(release)
            }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            UpdateCheckResult.Failed(failure.message ?: "Could not check for updates")
        }
    }

    fun cachedAvailableRelease(): AndroidRelease? =
        preferences.getString(KEY_RESULT, null)?.let { runCatching { AndroidRelease.fromJson(it) }.getOrNull() }

    private data class HttpResponse(val code: Int, val body: String, val headers: Map<String, String>)

    private fun request(url: String, etag: String?): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Netray-Android")
            if (!etag.isNullOrBlank()) setRequestProperty("If-None-Match", etag)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = if (code == HttpURLConnection.HTTP_NOT_MODIFIED || stream == null) "" else stream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_API_BODY) throw IOException("GitHub release response is too large")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
            val headers = buildMap {
                connection.headerFields.forEach { (name, values) ->
                    if (name != null && !values.isNullOrEmpty()) put(name, values.first())
                }
            }
            return HttpResponse(code, body, headers)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val PREFS = "android_update_state"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_ETAG = "releases_etag"
        private const val KEY_BODY = "releases_body"
        private const val KEY_RESULT = "available_release"
        private const val MAX_API_BODY = 2 * 1024 * 1024
    }
}
