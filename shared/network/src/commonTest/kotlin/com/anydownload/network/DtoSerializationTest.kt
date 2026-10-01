package com.anydownload.network

import com.anydownload.core.domain.DownloadOptions
import com.anydownload.core.domain.DownloadRequest
import com.anydownload.core.domain.MediaType
import com.anydownload.core.domain.QualityPreference
import com.anydownload.core.domain.StartPolicy
import com.anydownload.core.domain.VideoContainerProfile
import com.anydownload.network.dto.CreateJobRequestDto
import com.anydownload.network.dto.toDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DtoSerializationTest {
    private val json = AnyDownloadJson

    @Test
    fun createJobRequestSerializesTheDocumentedShape() {
        val request = DownloadRequest(
            sourceUrl = "https://media.example.org/watch?v=1",
            options = DownloadOptions(
                mediaType = MediaType.AUDIO,
                videoProfile = VideoContainerProfile.MP4,
                quality = QualityPreference.Resolution("192"),
                playlistItemLimit = 1,
                destinationFolder = "audio",
                startPolicy = StartPolicy.MANUAL,
            ),
        )

        val encoded = json.encodeToString(request.toDto())
        val element = json.parseToJsonElement(encoded) as JsonObject

        assertEquals("https://media.example.org/watch?v=1", element["url"]?.jsonPrimitive?.content)
        assertEquals("audio", element["mediaType"]?.jsonPrimitive?.content)
        assertEquals("mp4", element["profile"]?.jsonPrimitive?.content)
        assertEquals("manual", element["startPolicy"]?.jsonPrimitive?.content)
        assertEquals("audio", element["destination"]?.let { (it as JsonObject)["folder"] }?.jsonPrimitive?.content)
        assertEquals(1, element["playlist"]?.let { (it as JsonObject)["itemLimit"] }?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun unknownFieldsAndNullsAreTolerated() {
        val payload = """
            {
              "url": "https://media.example.org/a",
              "mediaType": "video",
              "futureServerField": {"nested": true},
              "profile": null
            }
        """.trimIndent()

        val decoded = json.decodeFromString<CreateJobRequestDto>(payload)

        assertEquals("https://media.example.org/a", decoded.url)
        assertEquals("video", decoded.mediaType)
        assertNull(decoded.profile)
    }
}
