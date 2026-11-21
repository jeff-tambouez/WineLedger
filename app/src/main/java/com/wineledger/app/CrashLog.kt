package com.wineledger.app

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Saves the last crash to a file so it can be read after the restart and shown to the person.
 * The app has no network permission: a report only leaves the phone if the person exports it.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"

    /** Chains our handler before the system one, so the crash is still reported to Android. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(describe(error, thread.name)) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun describe(error: Throwable, threadName: String? = null): String = buildString {
        appendLine("Wine Ledger crash report")
        appendLine("when: ${runCatching { Instant.now().toString() }.getOrDefault("?")}")
        appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        if (threadName != null) appendLine("thread: $threadName")
        appendLine()
        append(trace(error))
    }

    fun trace(error: Throwable): String =
        StringWriter().also { w -> PrintWriter(w).use { error.printStackTrace(it) } }.toString()

    fun read(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE)
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
