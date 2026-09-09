package com.aichat.client.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** 图片工具:读取、压缩、转 base64(多模态输入用) */
object ImageUtils {

    private const val MAX_EDGE = 1280

    /** 从 Uri 读取图片,等比压缩到最长边 1280px 内,JPEG 85 质量转 base64 */
    fun uriToChatImage(context: Context, uri: Uri): ChatImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bitmap.recycle()
        ChatImage(
            base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
            mime = "image/jpeg"
        )
    }.getOrNull()

    /** base64 转位图(发送前预览用) */
    fun base64ToBitmap(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
