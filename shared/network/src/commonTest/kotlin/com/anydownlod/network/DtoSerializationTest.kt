package com.anydownlod.network

import com.anydownlod.core.domain.DownloadOptions
import com.anydownlod.core.domain.DownloadRequest
import com.anydownlod.core.domain.MediaType
import com.anydownlod.core.domain.QualityPreference
import com.anydownlod.core.domain.StartPolicy
import com.anydownlod.core.domain.VideoContainerProfile
import com.anydownlod.network.dto.CreateJobRequestDto
import com.anydownlod.network.dto.toDto
import kotlinx.serialization.json.Json
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
