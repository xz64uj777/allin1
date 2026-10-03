package com.phoneguard

import android.content.Context
import android.provider.MediaStore
import java.util.concurrent.TimeUnit

data class StorageItem(val name: String, val uri: String, val bytes: Long, val ageDays: Long, val type: String)

object FileScanner {
    fun recentMedia(context: Context, limit: Int = 500): List<StorageItem> {
        val result = mutableListOf<StorageItem>()
        val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MIME_TYPE, MediaStore.Files.FileColumns._ID)
        context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, null, null,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { c ->
            val n = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val s = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val d = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val m = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val id = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            while (c.moveToNext() && result.size < limit) {
                val modified = c.getLong(d) * 1000
                result += StorageItem(c.getString(n) ?: "Unnamed",
                    MediaStore.Files.getContentUri("external").buildUpon().appendPath(c.getLong(id).toString()).build().toString(),
                    c.getLong(s).coerceAtLeast(0),
                    TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - modified).coerceAtLeast(0)),
                    c.getString(m) ?: "unknown")
            }
        }
        return result
    }
    fun large(items: List<StorageItem>, thresholdBytes: Long = 100L * 1024 * 1024) =
        items.filter { it.bytes >= thresholdBytes }.sortedByDescending { it.bytes }
}
