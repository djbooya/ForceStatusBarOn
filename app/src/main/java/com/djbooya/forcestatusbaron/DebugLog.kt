package com.djbooya.forcestatusbaron

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text log at Android/data/<package>/files/logs/forcestatusbaron.log, readable with a
 * file manager on units without ADB. Files older than 24 hours are removed on init.
 */
object DebugLog {
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (logFile != null) return
        val logsDir = File(context.getExternalFilesDir(null), "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }
        cleanupOldLogs(logsDir)
        logFile = File(logsDir, "forcestatusbaron.log")
    }

    fun i(tag: String, message: String) {
        log("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        log("WARN", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        log("ERROR", tag, msg)
    }

    private fun log(level: String, tag: String, message: String) {
        android.util.Log.println(
            if (level == "ERROR") android.util.Log.ERROR else android.util.Log.INFO, tag, message
        )
        val file = logFile ?: return
        try {
            file.appendText("[${dateFormat.format(Date())}] $level/$tag: $message\n")
        } catch (e: Exception) {
            android.util.Log.e("DebugLog", "Failed to write log", e)
        }
    }

    private fun cleanupOldLogs(logsDir: File) {
        try {
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000
            logsDir.listFiles()?.forEach { file ->
                if (file.isFile && now - file.lastModified() > oneDayMs) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DebugLog", "Failed to cleanup old logs", e)
        }
    }
}
