package com.wineledger.app.data

import android.content.Context
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Copies the database files out of the app without opening them through Room.
 * This keeps working when the database cannot be opened at all, so the wines can always be rescued.
 */
object DbRescue {
    const val DB_NAME = "wineledger.db"

    /** The main file plus its write-ahead log and shared-memory companions, when they exist. */
    fun files(context: Context): List<File> {
        val main = context.applicationContext.getDatabasePath(DB_NAME)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm")).filter { it.isFile }
    }

    fun describe(context: Context): String {
        val list = files(context)
        if (list.isEmpty()) return "No database file on this phone."
        return list.joinToString(", ") { "${it.name} ${it.length() / 1024} kB" }
    }

    fun writeZip(context: Context, out: OutputStream) {
        val list = files(context)
        if (list.isEmpty()) throw ImportException("No database file was found on this phone.")
        ZipOutputStream(out.buffered()).use { zip ->
            for (f in list) {
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
