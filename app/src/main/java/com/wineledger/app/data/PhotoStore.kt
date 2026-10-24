package com.wineledger.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Photos live in the app's private storage only: a 1600 px JPEG plus a small thumbnail.
 * No storage permission is needed (photo picker and system camera hand us content we copy).
 */
class PhotoStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "photos").apply { mkdirs() }
    private val cameraDir = File(app.cacheDir, "camera").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)
    fun thumb(name: String): File = File(dir, THUMB_PREFIX + name)

    /** Thumbnail if present, otherwise the full image. */
    fun displayFile(name: String, small: Boolean): File {
        if (small) {
            val t = thumb(name)
            if (t.exists()) return t
        }
        return file(name)
    }

    /** Destination of a camera shot: the file and the content Uri to pass to TakePicture. */
    fun newCameraTarget(): Pair<File, Uri> {
        cameraDir.mkdirs()
        val f = File(cameraDir, "shot_" + UUID.randomUUID().toString() + ".jpg")
        val uri = FileProvider.getUriForFile(app, app.packageName + ".files", f)
        return f to uri
    }

    /** Copy an image chosen with the photo picker into private storage. Returns the stored name or null. */
    fun importUri(uri: Uri): String? = try {
        store(ImageDecoder.createSource(app.contentResolver, uri))
    } catch (e: Exception) {
        null
    }

    /** Import a camera shot, then remove the temporary file. */
    fun importFile(f: File): String? = try {
        if (!f.exists() || f.length() == 0L) null else store(ImageDecoder.createSource(f))
    } catch (e: Exception) {
        null
    } finally {
        f.delete()
    }

    private fun store(source: ImageDecoder.Source): String {
        val full = decode(source, MAX_SIDE)
        val name = UUID.randomUUID().toString() + ".jpg"
        write(full, file(name), 86)
        val small = scaleDown(full, THUMB_SIDE)
        write(small, thumb(name), 80)
        if (small !== full) small.recycle()
        full.recycle()
        return name
    }

    private fun decode(source: ImageDecoder.Source, maxSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val ratio = maxSide.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (info.size.width * ratio).toInt()),
                    max(1, (info.size.height * ratio).toInt()),
                )
            }
        }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            max(1, (src.width * ratio).toInt()),
            max(1, (src.height * ratio).toInt()),
            true,
        )
    }

    private fun write(bmp: Bitmap, f: File, quality: Int) {
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** Used by restore: write raw bytes and rebuild the thumbnail. */
    fun restore(name: String, bytes: ByteArray) {
        val f = file(name)
        f.writeBytes(bytes)
        try {
            val full = decode(ImageDecoder.createSource(f), MAX_SIDE)
            val small = scaleDown(full, THUMB_SIDE)
            write(small, thumb(name), 80)
            if (small !== full) small.recycle()
            full.recycle()
        } catch (e: Exception) {
            // the full image is still usable without a thumbnail
        }
    }

    fun delete(name: String?) {
        if (name == null) return
        file(name).delete()
        thumb(name).delete()
    }

    fun allNames(): List<String> =
        (dir.list() ?: emptyArray()).filter { !it.startsWith(THUMB_PREFIX) }

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Remove stored photos no row refers to any more. */
    fun prune(referenced: Set<String>) {
        allNames().filter { it !in referenced }.forEach { delete(it) }
    }

    companion object {
        private const val THUMB_PREFIX = "t_"
        private const val MAX_SIDE = 1600
        private const val THUMB_SIDE = 360
    }
}
