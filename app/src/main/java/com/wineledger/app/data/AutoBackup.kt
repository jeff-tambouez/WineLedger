package com.wineledger.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Writes the full backup ZIP into a folder the person chose (Drive, Documents, an SD card…),
 * at most once a week, and keeps the last [KEEP] copies there.
 *
 * Its settings live in their own preferences file, outside Android's backup: the folder permission
 * belongs to this phone and would mean nothing after a restore on another one.
 */
object AutoBackup {
    const val KEEP = 5
    const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    private const val PREFIX = "wine-ledger-auto-"
    private const val PREFS = "autobackup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_LAST_AUTO = "last_auto"
    private const val KEY_LAST_ANY = "last_any"
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The chosen folder, only while the app still holds its permission. */
    fun folder(context: Context): Uri? {
        val uri = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse) ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return uri.takeIf { granted }
    }

    fun setFolder(context: Context, uri: Uri?) {
        val cr = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)?.takeIf { it != uri }?.let { old ->
            runCatching { cr.releasePersistableUriPermission(old, flags) }
        }
        if (uri != null) cr.takePersistableUriPermission(uri, flags)
        prefs(context).edit().apply {
            if (uri == null) remove(KEY_FOLDER) else putString(KEY_FOLDER, uri.toString())
            // A new folder gets its first copy right away.
            remove(KEY_LAST_AUTO)
        }.apply()
    }

    fun folderName(context: Context): String? {
        val tree = folder(context) ?: return null
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return runCatching {
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: "the chosen folder"
    }

    /** Time of the last backup of any kind (automatic, or the full backup saved by hand); 0 if never. */
    fun lastAny(context: Context): Long = prefs(context).getLong(KEY_LAST_ANY, 0L)

    fun lastAuto(context: Context): Long = prefs(context).getLong(KEY_LAST_AUTO, 0L)

    fun markBackedUp(context: Context, auto: Boolean, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().apply {
            putLong(KEY_LAST_ANY, now)
            if (auto) putLong(KEY_LAST_AUTO, now)
        }.apply()
    }

    fun isDue(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        folder(context) != null && now - lastAuto(context) >= INTERVAL_MS

    /** Creates a new ZIP in the folder, fills it with [fill], then removes the oldest copies. Returns its name. */
    fun write(context: Context, fill: (OutputStream) -> Unit): String {
        val tree = folder(context) ?: throw ImportException("Choose a backup folder first.")
        val cr = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + LocalDateTime.now().format(STAMP) + ".zip"
        val doc = DocumentsContract.createDocument(cr, parent, "application/zip", name)
            ?: throw ImportException("The backup folder cannot be written to.")
        try {
            cr.openOutputStream(doc, "w")!!.use(fill)
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(cr, doc) }
            throw e
        }
        markBackedUp(context, auto = true)
        prune(context, tree)
        return name
    }

    /** Keeps the newest [KEEP] automatic copies. The names sort by date; other files are never touched. */
    private fun prune(context: Context, tree: Uri) {
        val cr = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val ours = ArrayList<Pair<String, String>>()
        runCatching {
            cr.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val n = c.getString(1) ?: continue
                    if (n.startsWith(PREFIX) && n.endsWith(".zip")) ours += id to n
                }
            }
        }
        ours.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
