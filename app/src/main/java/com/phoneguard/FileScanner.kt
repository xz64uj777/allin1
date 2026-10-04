package com.phoneguard

import android.content.Context
import android.provider.MediaStore
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class StorageItem(
    val name: String,
    val uri: String,
    val bytes: Long,
    val ageDays: Long,
    val type: String,
    val sha256: String? = null
)

object FileScanner {
    fun recentMedia(context: Context, limit: Int = 500): List<StorageItem> {
        val result = mutableListOf<StorageItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns._ID
        )
        val base = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(base, projection, null, null, "\${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            val n = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val s = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val d = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val m = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val id = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (c.moveToNext() && result.size < limit) {
                val modified = c.getLong(d) * 1000
                result += StorageItem(
                    c.getString(n) ?: "Unnamed",
                    base.buildUpon().appendPath(c.getLong(id).toString()).build().toString(),
                    c.getLong(s).coerceAtLeast(0),
                    TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - modified).coerceAtLeast(0)),
                    c.getString(m) ?: "unknown"
                )
            }
        }
        return result
    }

    fun large(items: List<StorageItem>, thresholdBytes: Long = 100L * 1024 * 1024) =
        items.filter { it.bytes >= thresholdBytes }.sortedByDescending { it.bytes }

    fun suspiciousExtension(items: List<StorageItem>): List<StorageItem> {
        val risky = setOf("apk", "xapk", "dex", "jar", "sh", "bat", "cmd", "scr")
        return items.filter { it.name.substringAfterLast('.', "").lowercase() in risky }
    }

    fun sha256(context: Context, uriString: String): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
            BufferedInputStream(input).use { stream ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    fun withHashes(context: Context, items: List<StorageItem>, maxFiles: Int = 100): List<StorageItem> =
        items.take(maxFiles).map { it.copy(sha256 = sha256(context, it.uri)) }

    fun duplicateGroups(items: List<StorageItem>): List<List<StorageItem>> =
        items.filter { it.sha256 != null }.groupBy { it.sha256 }.values.filter { it.size > 1 }
}
