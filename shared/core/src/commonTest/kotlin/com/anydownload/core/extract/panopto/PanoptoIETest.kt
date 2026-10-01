package com.anydownload.core.extract.panopto

import com.anydownload.core.extract.ExtractorHttp
import com.anydownload.core.extract.InfoExtractor
import com.anydownload.core.extract.harness.CaseResult
import com.anydownload.core.extract.harness.Expect
import com.anydownload.core.extract.harness.ExtractorCase
import com.anydownload.core.extract.harness.ExtractorTestRun
import com.anydownload.core.extract.harness.FixtureHttpTransfer
import com.anydownload.core.extract.harness.FixtureRoute
import com.anydownload.core.extract.harness.runCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Fixture cases for the Panopto subset. Every id, host, and media address is
 * synthesized (`*.example`); no cookie, token, or signed URL appears.
 */
class PanoptoIETest {

    private fun transfer(vararg routes: FixtureRoute): FixtureHttpTransfer =
        FixtureHttpTransfer(routes.toList())

    private fun http(transfer: FixtureHttpTransfer): ExtractorHttp = ExtractorHttp(transfer)

    private val videoId = "26b3ae9e-4a48-4dcc-96ba-0befba08a0fb"
    private val videoUrl = "https://demo.hosted.panopto.com/Panopto/Pages/Viewer.aspx?id=$videoId"

    private val deliveryRoute = FixtureRoute(
        urlPattern = "https://demo.hosted.panopto.com/Panopto/Pages/Viewer/DeliveryInfo.aspx?deliveryId=*",
        contentType = "application/json",
        body = """
            {"Delivery": {
              "SessionName": "Fixture Panopto Title",
              "SessionAbstract": "Fixture description",
              "Duration": 88.171,
              "SessionStartTime": 13099184200,
              "OwnerDisplayName": "Fixture Owner",
              "OwnerId": "owner-1",
              "SessionGroupPublicID": "group-1",
              "SessionGroupLongName": "Fixture Group",
              "PodcastStreams": [{"StreamHttpUrl": "https://media.example/panopto/podcast/master.m3u8",
                                  "ViewerMediaFileTypeName": "hls", "Tag": "PODCAST"}],
              "Streams": [{"StreamUrl": "https://media.example/panopto/video.mp4",
                           "ViewerMediaFileTypeName": "mp4", "Tag": "main"}],
              "Timestamps": [{"Caption": "Intro", "Time": 0, "Duration": 10},
                             {"Caption": "Part 2", "Time": 10, "Duration": 20}]
            }}
        """.trimIndent(),
    )

    // ------------------------------------------------------------ URL matching

    @Test
    fun urlFormsMatchPerClass() {
        val cases = listOf<Pair<InfoExtractor, String>>(
            PanoptoIE(http(transfer())) to videoUrl,
            PanoptoIE(http(transfer())) to
                "https://unisa.au.panopto.com/Panopto/Pages/Embed.aspx?id=9d9a0fa3-e99a-4ebd-a281-aac2017f4da4",
            PanoptoPlaylistIE(http(transfer())) to
                "https://howtovideos.hosted.panopto.com/Panopto/Pages/Viewer.aspx?pid=f3b39fcf-882f-4849-93d6-a9f401236d36",
            PanoptoListIE(http(transfer())) to
                "https://demo.hosted.panopto.com/Panopto/Pages/Sessions/List.aspx#folderID=%22e4c6a2fc-1214-4ca0-8fb7-aef2e29ff63a%22",
        )
        for ((extractor, url) in cases) {
            assertTrue(extractor.suitable(url), "${extractor.ieKey} must match: $url")
        }
        assertFalse(PanoptoIE(http(transfer())).suitable(
            "https://howtovideos.hosted.panopto.com/Panopto/Pages/Viewer.aspx?pid=f3b39fcf-882f-4849-93d6-a9f401236d36",
        ))
        assertFalse(PanoptoListIE(http(transfer())).suitable(videoUrl))
    }

    // --------------------------------------------------------------- PanoptoIE

    @Test
    fun deliveryMapsStreamsMetadataAndChapters() = runTest {
        val transfer = transfer(deliveryRoute)
        val info = PanoptoIE(http(transfer)).extract(videoUrl)

        assertEquals(videoId, info.id)
        assertEquals("Fixture Panopto Title", info.title)
        assertEquals("Fixture description", info.description)
        assertEquals(88.171, info.duration)
        assertEquals("20160328", info.uploadDate)
        assertEquals("Fixture Owner", info.uploader)
        assertEquals("group-1", info.channelId)
        assertEquals("Fixture Group", info.channel)
        assertEquals(2, info.formats.size)
        assertEquals("m3u8_native", info.formats[0].protocol)
        assertEquals("PODCAST", info.formats[0].formatNote)
        assertEquals("mp4", info.formats[1].ext)
        assertEquals(-10, info.formats[1].preference)
        assertEquals(2, info.chapters.size)
        assertEquals("Part 2", info.chapters[1].title)
        assertTrue(info.thumbnails.single().url.contains("FrameGrabber.svc"))
    }

    // ------------------------------------------------------- PanoptoPlaylistIE

    @Test
    fun playlistListsTheSessionItems() = runTest {
        val url = "https://howtovideos.hosted.panopto.com/Panopto/Pages/Viewer.aspx" +
            "?pid=f3b39fcf-882f-4849-93d6-a9f401236d36"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://howtovideos.hosted.panopto.com/Panopto/Api/Playlists/" +
                    "f3b39fcf-882f-4849-93d6-a9f401236d36",
                contentType = "application/json",
                body = """
                    {"Name": "Fixture Playlist", "Description": "Fixture playlist description",
                     "SessionListId": "list-1"}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://howtovideos.hosted.panopto.com/Panopto/Api/SessionLists/list-1" +
                    "?collections[0].maxCount=500&collections[0].name=items",
                contentType = "application/json",
                body = """
                    {"Items": [
                      {"TypeName": "Session", "Id": "s1", "Name": "Session 1",
                       "ViewerUri": "/Panopto/Pages/Viewer.aspx?id=$videoId"},
                      {"TypeName": "Folder", "Id": "f1"}
                    ]}
                """.trimIndent(),
            ),
        )
        val info = PanoptoPlaylistIE(http(transfer)).extract(url)
        assertEquals("f3b39fcf-882f-4849-93d6-a9f401236d36", info.id)
        assertEquals("Fixture Playlist", info.title)
        assertEquals(1, info.entries.size)
        assertEquals(
            "https://howtovideos.hosted.panopto.com/Panopto/Pages/Viewer.aspx?id=$videoId",
            info.entries[0].url,
        )
    }

    // ----------------------------------------------------------- PanoptoListIE

    @Test
    fun listPagesTheSessionsAndSubfolders() = runTest {
        val folderId = "e4c6a2fc-1214-4ca0-8fb7-aef2e29ff63a"
        val url = "https://demo.hosted.panopto.com/Panopto/Pages/Sessions/List.aspx" +
            "#folderID=%22$folderId%22"
        val transfer = transfer(
            FixtureRoute(
                urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetFolderInfo",
                method = "POST",
                contentType = "application/json",
                body = """{"Name": "Showcase Videos"}""",
            ),
            FixtureRoute(
                urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetSessions",
                method = "POST",
                contentType = "application/json",
                requestBodyContains = "\"page\":0",
                body = """
                    {"Results": [{"DeliveryID": "d1", "SessionName": "Session A"}],
                     "Subfolders": [{"ID": "sub1", "Name": "Subfolder"}]}
                """.trimIndent(),
            ),
            FixtureRoute(
                urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetSessions",
                method = "POST",
                contentType = "application/json",
                requestBodyContains = "\"page\":1",
                body = """{"Results": [], "Subfolders": []}""",
            ),
        )
        val info = PanoptoListIE(http(transfer)).extract(url)
        assertEquals(folderId, info.id)
        assertEquals("Showcase Videos", info.title)
        assertEquals(2, info.entries.size)
        assertEquals("Session A", info.entries[0].title)
        assertEquals("Subfolder", info.entries[1].title)
        assertTrue(info.entries[1].url!!.contains("folderID=\"sub1\""))
    }

    // --------------------------------------------------------------- harness

    @Test
    fun panoptoIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val case = ExtractorCase(
            url = videoUrl,
            infoDict = mapOf(
                "id" to Expect.Value(videoId),
                "title" to Expect.Value("Fixture Panopto Title"),
                "duration" to Expect.Value(88.171),
                "upload_date" to Expect.Value("20160328"),
                "formats.0.protocol" to Expect.Value("m3u8_native"),
            ),
            routes = listOf(deliveryRoute),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PanoptoIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }

    @Test
    fun panoptoListIsAHarnessCaseFromSyntheticFixtures() = runTest {
        val folderId = "e4c6a2fc-1214-4ca0-8fb7-aef2e29ff63a"
        val url = "https://demo.hosted.panopto.com/Panopto/Pages/Sessions/List.aspx" +
            "#folderID=%22$folderId%22"
        val case = ExtractorCase(
            url = url,
            infoDict = mapOf(
                "id" to Expect.Value(folderId),
                "title" to Expect.Value("Showcase Videos"),
                "entries" to Expect.Count(1),
            ),
            routes = listOf(
                FixtureRoute(
                    urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetFolderInfo",
                    method = "POST",
                    contentType = "application/json",
                    body = """{"Name": "Showcase Videos"}""",
                ),
                FixtureRoute(
                    urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetSessions",
                    method = "POST",
                    contentType = "application/json",
                    requestBodyContains = "\"page\":0",
                    body = """{"Results": [{"DeliveryID": "d1", "SessionName": "Session A"}]}""",
                ),
                FixtureRoute(
                    urlPattern = "https://demo.hosted.panopto.com/Panopto/Services/Data.svc/GetSessions",
                    method = "POST",
                    contentType = "application/json",
                    requestBodyContains = "\"page\":1",
                    body = """{"Results": [], "Subfolders": []}""",
                ),
            ),
        )
        val result = runCase(case, ExtractorTestRun()) { h -> PanoptoListIE(h) }
        assertIs<CaseResult.Passed>(result, "case failed: $result")
    }
}
