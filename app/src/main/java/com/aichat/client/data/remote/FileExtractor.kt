package com.aichat.client.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** 用户选中的文档:名称、字节数、抽取出的正文 */
data class ChatFile(
    val name: String,
    val sizeBytes: Long,
    val text: String
)

/**
 * 本地文档解析:PDF、Word(docx)、TXT/Markdown 等纯文本。
 * 不经过任何服务器;解析失败给出可操作的中文提示。
 */
object FileExtractor {

    private const val MAX_CHARS = 60_000

    fun extract(context: Context, uri: Uri): Result<ChatFile> = runCatching {
        val name = displayName(context, uri)
        val size = sizeOf(context, uri)
        val lower = name.lowercase()
        val raw = when {
            lower.endsWith(".pdf") -> readPdf(context, uri)
            lower.endsWith(".docx") -> readDocx(context, uri)
            lower.endsWith(".doc") -> error("旧版 .doc 无法解析,请在 Word 里另存为 .docx 再上传")
            lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") ||
                lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".log") ||
                lower.endsWith(".kt") || lower.endsWith(".java") || lower.endsWith(".py") ->
                readText(context, uri)
            else -> error("暂不支持该格式:只支持 PDF、DOCX、TXT、Markdown 与常见代码文本")
        }.trim()
        if (raw.isBlank()) error("文档里没有抽取到文字(可能是扫描件或图片型 PDF)")
        val text = if (raw.length > MAX_CHARS) {
            raw.take(MAX_CHARS) + "\n\n[文档过长,已截断,仅保留前 ${MAX_CHARS} 字]"
        } else raw
        ChatFile(name, size, text)
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "文档"
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) return c.getLong(idx)
        }
        return -1L
    }

    private fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取文件")
        return String(bytes, Charsets.UTF_8)
    }

    private fun readPdf(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: error("无法读取 PDF")
        stream.use { input ->
            PDDocument.load(input).use { doc ->
                return PDFTextStripper().getText(doc)
            }
        }
    }

    /** docx 是 zip,正文在 word/document.xml;按段落拆行后去掉标签 */
    private fun readDocx(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri) ?: error("无法读取 DOCX")
        val xml = ByteArrayOutputStream()
        ZipInputStream(stream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    zip.copyTo(xml)
                    break
                }
                entry = zip.nextEntry
            }
        }
        val raw = xml.toString("UTF-8")
        if (raw.isBlank()) error("DOCX 结构异常,找不到正文")
        return raw
            .replace("</w:p>", "\n")
            .replace("<w:br/>", "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
    }
}
