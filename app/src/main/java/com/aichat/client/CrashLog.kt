package com.aichat.client

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.PrintWriter
import java.io.StringWriter

/** 把未捕获异常写到「下载/aurora-crash.txt」,便于在无法连 adb 时定位闪退 */
object CrashLog {

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        if (Build.VERSION.SDK_INT < 29) return
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val text = buildString {
            append("time=").append(System.currentTimeMillis()).append('\n')
            append("thread=").append(thread.name).append('\n')
            append("version=").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append(sw.toString()).append("\n\n")
        }
        val resolver = context.contentResolver
        val name = "aurora-crash.txt"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val existing = runCatching {
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(name), null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        }.getOrDefault(-1L)
        val uri = if (existing > 0) {
            ContentUris.withAppendedId(collection, existing)
        } else {
            resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            })
        } ?: return
        resolver.openOutputStream(uri, "wa")?.use { it.write(text.toByteArray()) }
    }
}
