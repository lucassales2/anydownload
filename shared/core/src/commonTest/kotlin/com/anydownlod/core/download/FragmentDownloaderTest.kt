/*
 * Fragment downloader tests — AnyDownload (T-073)
 *
 * Sequential fetch, byte-exact assembly, AES-128 decryption, bounded retries,
 * and cancellation. Unlicense; see shared/core/NOTICE.md. All bytes synthetic.
 */
package com.anydownlod.core.download

import com.anydownlod.core.extract.MediaFragment
import com.anydownlod.core.platform.HttpBody
import com.anydownlod.core.platform.HttpRequest
import com.anydownlod.core.platform.HttpResponse
import com.anydownlod.core.platform.HttpTransfer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FragmentDownloaderTest {

    private class ArrayBody(private val bytes: ByteArray) : HttpBody {
        private var offset = 0
        override suspend fun readNext(buffer: ByteArray): Int {
            if (offset >= bytes.size) return -1
            val count = minOf(buffer.size, bytes.size - offset)
            bytes.copyInto(buffer, 0, offset, offset + count)
            offset += count
            return count
        }

        override suspend fun close() = Unit
    }

    private class FakeTransfer(
        private val bodies: Map<String, ByteArray>,
        private val failuresBeforeSuccess: MutableMap<String, Int> = mutableMapOf(),
    ) : HttpTransfer {
        val requests = mutableListOf<HttpRequest>()

        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            val remaining = failuresBeforeSuccess[request.url] ?: 0
            if (remaining > 0) {
                failuresBeforeSuccess[request.url] = remaining - 1
                return HttpResponse.Failed(com.anydownlod.core.platform.HttpFailureReason.NETWORK, "transient")
            }
            val bytes = bodies[request.url] ?: return HttpResponse.Final(404)
            val ranged = if (request.range != null) {
                val start = request.range.first.toInt()
                val end = minOf(request.range.last.toInt(), bytes.size - 1)
                bytes.copyOfRange(start, end + 1)
            } else {
                bytes
            }
            return HttpResponse.Final(200, body = ArrayBody(ranged))
        }
    }

    private val key = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(16) { (it + 16).toByte() }

    @Test
    fun concatenatesTsFragmentsInOrder() = runTest {
        val transfer = FakeTransfer(
            mapOf(
                "https://media.example/a.ts" to "AAA".encodeToByteArray(),
                "https://media.example/b.ts" to "BBB".encodeToByteArray(),
                "https://media.example/c.ts" to "CCC".encodeToByteArray(),
            ),
        )
        val written = mutableListOf<Byte>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val outcome = FragmentDownloader(transfer).download(
            fragments = listOf(
                MediaFragment("https://media.example/a.ts"),
                MediaFragment("https://media.example/b.ts"),
                MediaFragment("https://media.example/c.ts"),
            ),
            onChunk = { written += it.toList() },
            onProgress = { completed, total, _ -> progress += completed to total },
        )
        assertIs<FragmentOutcome.Completed>(outcome)
        assertContentEquals("AAABBBCCC".encodeToByteArray(), written.toByteArray())
        assertEquals(listOf(1 to 3, 2 to 3, 3 to 3), progress)
    }

    @Test
    fun fmp4VariantWritesInitSegmentFirst() = runTest {
        val transfer = FakeTransfer(
            mapOf(
                "https://media.example/init.mp4" to "INIT".encodeToByteArray(),
                "https://media.example/seg0.m4s" to "SEG0".encodeToByteArray(),
                "https://media.example/seg1.m4s" to "SEG1".encodeToByteArray(),
            ),
        )
        val written = mutableListOf<Byte>()
        val outcome = FragmentDownloader(transfer).download(
            fragments = listOf(
                MediaFragment("https://media.example/seg0.m4s"),
                MediaFragment("https://media.example/seg1.m4s"),
            ),
            initSegment = MediaFragment("https://media.example/init.mp4"),
            onChunk = { written += it.toList() },
            onProgress = { _, _, _ -> },
        )
        assertIs<FragmentOutcome.Completed>(outcome)
        assertContentEquals("INITSEG0SEG1".encodeToByteArray(), written.toByteArray())
    }

    @Test
    fun aes128FragmentsAreDecryptedByteExact() = runTest {
        val first = "8997c6837d7190199fa790420922462e301bfa64f5465b96e68a47d365e8ace2"
        val second = "e2b686daaf86fd514e3d7706c3ed4374c59f650e806fe9de13afb9a35926e862"
        fun hex(value: String) = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val transfer = FakeTransfer(
            mapOf(
                "https://media.example/key.bin" to key,
                "https://media.example/enc0.ts" to hex(first),
                "https://media.example/enc1.ts" to hex(second),
            ),
        )
        val written = mutableListOf<Byte>()
        val outcome = FragmentDownloader(transfer).download(
            fragments = listOf(
                MediaFragment("https://media.example/enc0.ts"),
                MediaFragment("https://media.example/enc1.ts"),
            ),
            key = Aes128KeyInfo("https://media.example/key.bin", iv),
            onChunk = { written += it.toList() },
            onProgress = { _, _, _ -> },
        )
        assertIs<FragmentOutcome.Completed>(outcome)
        assertContentEquals("TS-FRAGMENT-ONE\nTS-FRAGMENT-TWO\n".encodeToByteArray(), written.toByteArray())
    }

    @Test
    fun rangedFragmentsSendRangeHeaders() = runTest {
        val transfer = FakeTransfer(mapOf("https://media.example/range.ts" to ByteArray(100) { it.toByte() }))
        val written = mutableListOf<Byte>()
        val outcome = FragmentDownloader(transfer).download(
            fragments = listOf(MediaFragment("https://media.example/range.ts", rangeStart = 10, rangeEnd = 19)),
            onChunk = { written += it.toList() },
            onProgress = { _, _, _ -> },
        )
        assertIs<FragmentOutcome.Completed>(outcome)
        assertEquals(10L..19L, transfer.requests.single().range)
        assertEquals(10, written.size)
    }

    @Test
    fun transientFailuresAreRetried() = runTest {
        val transfer = FakeTransfer(
            bodies = mapOf("https://media.example/a.ts" to "OK".encodeToByteArray()),
            failuresBeforeSuccess = mutableMapOf("https://media.example/a.ts" to 1),
        )
        val written = mutableListOf<Byte>()
        val outcome = FragmentDownloader(transfer, maxRetries = 2).download(
            fragments = listOf(MediaFragment("https://media.example/a.ts")),
            onChunk = { written += it.toList() },
            onProgress = { _, _, _ -> },
        )
        assertIs<FragmentOutcome.Completed>(outcome)
        assertEquals(2, transfer.requests.size)
    }

    @Test
    fun cancelStopsBetweenFragments() = runTest {
        val transfer = FakeTransfer(
            mapOf(
                "https://media.example/a.ts" to "AAA".encodeToByteArray(),
                "https://media.example/b.ts" to "BBB".encodeToByteArray(),
            ),
        )
        var completed = 0
        val outcome = FragmentDownloader(transfer).download(
            fragments = listOf(
                MediaFragment("https://media.example/a.ts"),
                MediaFragment("https://media.example/b.ts"),
            ),
            onChunk = { completed++ },
            onProgress = { _, _, _ -> },
            isCancelled = { completed >= 1 },
        )
        assertIs<FragmentOutcome.Cancelled>(outcome)
        assertEquals(1, transfer.requests.size)
    }

    @Test
    fun exhaustedRetriesFailTyped() = runTest {
        val transfer = FakeTransfer(bodies = emptyMap())
        val outcome = FragmentDownloader(transfer, maxRetries = 1).download(
            fragments = listOf(MediaFragment("https://media.example/missing.ts")),
            onChunk = { },
            onProgress = { _, _, _ -> },
        )
        assertIs<FragmentOutcome.Failed>(outcome)
        assertTrue(outcome.reason.isNotBlank())
    }
}
