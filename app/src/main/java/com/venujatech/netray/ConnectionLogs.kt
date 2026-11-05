package com.venujatech.netray

/** Reads only this process; polling stops when the log dialog closes. */
internal object ConnectionLogs {
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}")
    private val endpoint = Regex("\\b(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?::[0-9]+)?|\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?")

    fun sanitize(line: String): String =
        if (line.contains("vless://", true) || line.contains('{')) "[Configuration details hidden]"
        else endpoint.replace(uuid.replace(line.take(1500), "[credential]"), "[endpoint]")

    fun read(): List<String> = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-t", "200", "--pid=${android.os.Process.myPid()}",
            "-v", "time", "NetrayVpn:V", "GoLog:W", "Go:E", "AndroidRuntime:E", "*:S")
            .redirectErrorStream(true).start()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.take(200).map { line ->
                    sanitize(line)
                }.toList().ifEmpty { listOf("No logs yet. Tap Connect to start the VPN.") }
            }
        } finally { process.destroy() }
    }.getOrElse { listOf("Could not read connection logs: ${it.javaClass.simpleName}") }
}
