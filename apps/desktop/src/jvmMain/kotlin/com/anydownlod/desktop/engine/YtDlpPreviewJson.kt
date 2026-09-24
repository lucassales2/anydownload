package com.anydownlod.desktop.engine

import com.anydownlod.core.MediaPreview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads one yt-dlp `--dump-single-json` document into a [MediaPreview].
 * Returns null when the document has no title. Process output other than the
 * JSON object is ignored.
 */
internal object YtDlpPreviewJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, requestedUrl: String): MediaPreview? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val element = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)) }.getOrNull()
        val obj = element as? JsonObject ?: return null
        val title = obj.text("title")?.takeIf { it.isNotBlank() } ?: return null
        val playlist = obj.text("_type") == "playlist" || obj.long("playlist_count") != null
        val entries = obj["entries"] as? JsonArray
        return MediaPreview(
            pageUrl = obj.text("webpage_url") ?: obj.text("original_url") ?: requestedUrl,
            title = title,
            thumbnailUrl = thumbnailOf(obj),
            channel = firstText(obj, "channel", "uploader", "playlist_channel", "creator", "playlist_uploader"),
            durationSeconds = obj.long("duration"),
            extractor = obj.text("extractor_key") ?: obj.text("extractor"),
            description = obj.text("description")?.let(::shorten),
            viewCount = obj.long("view_count"),
            uploadDate = obj.text("upload_date")?.let(::formatUploadDate),
            playlist = playlist,
            entryCount = obj.long("playlist_count")?.toInt() ?: entries?.size,
        )
    }

    private fun thumbnailOf(obj: JsonObject): String? {
        obj.text("thumbnail")?.let { return it }
        val thumbnails = obj["thumbnails"] as? JsonArray ?: return null
        return thumbnails.firstNotNullOfOrNull { item ->
            (item as? JsonObject)?.text("url")
        }
    }

    private fun firstText(obj: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> obj.text(key)?.takeIf { it.isNotBlank() } }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive.booleanOrNull != null) return null
        return primitive.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
    }

    private fun JsonObject.long(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.doubleOrNull?.toLong()
    }

    private fun shorten(description: String): String? {
        val collapsed = description.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return null
        return if (collapsed.length <= 280) collapsed else collapsed.take(277).trimEnd() + "…"
    }

    private fun formatUploadDate(raw: String): String {
        if (raw.length == 8 && raw.all { it.isDigit() }) {
            return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        }
        return raw
    }
}
