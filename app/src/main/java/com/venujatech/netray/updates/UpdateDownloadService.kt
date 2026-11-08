package com.venujatech.netray.updates

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.venujatech.netray.MainActivity
import com.venujatech.netray.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.Certificate

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val downloaded: Long, val total: Long) : UpdateDownloadState
    data class Ready(val apk: File, val release: AndroidRelease) : UpdateDownloadState
    data class Failed(val message: String, val apk: File? = null, val release: AndroidRelease? = null) : UpdateDownloadState
    data object Cancelled : UpdateDownloadState
}

class UpdateDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    private var currentPartial: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                currentPartial?.delete()
                mutableState.value = UpdateDownloadState.Cancelled
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (downloadJob?.isActive == true) return START_NOT_STICKY
                val release = intent.getStringExtra(EXTRA_RELEASE)?.let { runCatching { AndroidRelease.fromJson(it) }.getOrNull() }
                    ?: run { stopSelf(startId); return START_NOT_STICKY }
                startAsForeground()
                downloadJob = serviceScope.launch {
                    try {
                        mutableState.value = UpdateDownloadState.Downloading(0L, release.assetSize)
                        val apk = downloadVerify(release)
                        mutableState.value = UpdateDownloadState.Ready(apk, release)
                        updateNotification("Download verified", 100, true)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf(startId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        mutableState.value = UpdateDownloadState.Failed(failure.message ?: getString(R.string.update_download_failed), currentPartial?.takeIf(File::exists), release)
                        updateNotification(getString(R.string.update_download_failed), 0, false)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf(startId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun downloadVerify(release: AndroidRelease): File = withContext(Dispatchers.IO) {
        require(allowedDownloadUrl(release.downloadUrl)) { getString(R.string.update_error_unsafe_url) }
        val directory = File(cacheDir, "updates").apply { mkdirs() }
        cleanupOldDownloads(directory, release.assetName)
        val partial = File(directory, "${release.assetName}.part")
        val complete = File(directory, release.assetName)
        currentPartial = partial
        val free = StatFs(directory.path).availableBytes
        require(free >= maxOf(16L * 1024 * 1024, release.assetSize * 2)) { getString(R.string.update_error_storage) }

        downloadWithResume(release.downloadUrl, partial, release.assetSize)
        val expectedHash = release.digest?.removePrefix("sha256:")?.takeIf(String::isNotBlank)
            ?: release.checksumUrl?.let { checksumFor(it, release.assetName) }
            ?: throw IOException(getString(R.string.update_error_missing_checksum))
        require(sha256(partial).equals(expectedHash, ignoreCase = true)) { getString(R.string.update_error_checksum) }
        verifyApk(partial)
        if (complete.exists()) complete.delete()
        check(partial.renameTo(complete)) { getString(R.string.update_error_file_finalize) }
        currentPartial = null
        complete
    }

    private fun downloadWithResume(initialUrl: String, file: File, totalSize: Long) {
        var target = URL(initialUrl)
        var redirects = 0
        var connection: HttpURLConnection? = null
        while (true) {
            requireAllowedHost(target)
            connection = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Netray-Android")
                if (file.exists() && file.length() > 0) setRequestProperty("Range", "bytes=${file.length()}-")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: throw IOException(getString(R.string.update_error_redirect))
                connection.disconnect()
                if (++redirects > 5) throw IOException(getString(R.string.update_error_redirect))
                target = URL(target, location)
                requireAllowedHost(target)
                continue
            }
            break
        }
        val active = requireNotNull(connection)
        try {
            val code = active.responseCode
            require(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                getString(R.string.update_error_download_http, code)
            }
            val existing = file.length().takeIf { code == HttpURLConnection.HTTP_PARTIAL } ?: 0L
            if (code == HttpURLConnection.HTTP_OK) file.delete()
            val responseTotal = active.getHeaderField("Content-Length")?.toLongOrNull()?.plus(existing) ?: totalSize
            var lastNotification = 0L
            active.inputStream.use { input ->
                FileOutputStream(file, code == HttpURLConnection.HTTP_PARTIAL).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        val now = System.currentTimeMillis()
                        if (now - lastNotification >= 500) {
                            lastNotification = now
                            val downloaded = file.length()
                            mutableState.value = UpdateDownloadState.Downloading(downloaded, responseTotal)
                            val percent = if (responseTotal > 0) ((downloaded * 100) / responseTotal).toInt().coerceIn(0, 99) else 0
                            updateNotification("${downloaded / 1_000_000} / ${responseTotal / 1_000_000} MB", percent, false)
                        }
                    }
                }
            }
            if (totalSize > 0) require(file.length() == totalSize) { getString(R.string.update_error_incomplete_download) }
        } finally {
            active.disconnect()
        }
    }

    private fun checksumFor(checksumUrl: String, assetName: String): String {
        val url = URL(checksumUrl)
        requireAllowedHost(url)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Netray-Android")
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) { getString(R.string.update_error_missing_checksum) }
            val contents = connection.inputStream.bufferedReader().use { it.readText().take(64 * 1024) }
            val line = contents.lineSequence().firstOrNull { it.trim().endsWith(" $assetName") || it.trim().endsWith(" *$assetName") }
                ?: throw IOException(getString(R.string.update_error_missing_checksum))
            return line.trim().substringBefore(' ').trim()
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyApk(file: File) {
        val packageName = packageName
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: throw IOException(getString(R.string.update_error_invalid_apk))
        require(archive.packageName == packageName) { getString(R.string.update_error_package) }
        // minSdk is 28, so GET_SIGNING_CERTIFICATES with the int-flag overload is always available
        @Suppress("DEPRECATION")
        val installed = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archiveVersion = versionCode(archive)
        val installedVersion = versionCode(installed)
        require(archiveVersion > installedVersion) { getString(R.string.update_error_old_version) }
        val candidateCerts = certificates(archive)
        val installedCerts = certificates(installed)
        require(candidateCerts.isNotEmpty() && candidateCerts.toSet() == installedCerts.toSet()) {
            getString(R.string.update_error_signature)
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo): List<String> {
        val signatures: List<android.content.pm.Signature> = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02X".format(it) }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun allowedDownloadUrl(value: String): Boolean = runCatching { requireAllowedHost(URL(value)); true }.getOrDefault(false)

    private fun requireAllowedHost(url: URL) {
        require(url.protocol.equals("https", ignoreCase = true)) { getString(R.string.update_error_unsafe_url) }
        require(url.host.lowercase() in ALLOWED_RELEASE_HOSTS) { getString(R.string.update_error_unsafe_url) }
    }

    private fun cleanupOldDownloads(directory: File, keepName: String) {
        directory.listFiles()?.filter { it.name != "$keepName.part" && it.name != keepName }?.forEach(File::delete)
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.update_notification_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = notification(getString(R.string.update_downloading), 0, false)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(message: String, progress: Int, finished: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message, progress, finished))
    }

    private fun notification(message: String, progress: Int, finished: Boolean): Notification {
        val open = Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_UPDATER)
        val openIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = Intent(this, UpdateDownloadService::class.java).setAction(ACTION_CANCEL)
        val cancelIntent = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_vpn_status)
            .setContentTitle(getString(R.string.update_notification_title))
            .setContentText(message)
            .setContentIntent(openIntent)
            .setOngoing(!finished)
            .setOnlyAlertOnce(true)
        if (finished) builder.setProgress(0, 0, false)
        else builder.setProgress(100, progress, progress == 0).addAction(0, getString(R.string.action_cancel), cancelIntent)
        return builder.build()
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.venujatech.netray.UPDATE_DOWNLOAD"
        const val ACTION_CANCEL = "com.venujatech.netray.UPDATE_CANCEL"
        const val ACTION_OPEN_UPDATER = "com.venujatech.netray.OPEN_UPDATER"
        const val EXTRA_RELEASE = "release_json"
        private const val CHANNEL = "app_updates"
        private const val NOTIFICATION_ID = 73
        private val ALLOWED_RELEASE_HOSTS = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
        private val mutableState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        val state = mutableState.asStateFlow()

        fun start(context: Context, release: AndroidRelease) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .setAction(ACTION_START).putExtra(EXTRA_RELEASE, release.toJson())
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, UpdateDownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
