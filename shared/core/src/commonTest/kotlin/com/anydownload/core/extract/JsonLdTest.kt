package com.anydownload.core.extract

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cases for the `_yield_json_ld` block scanner and the `traverse_json_ld`
 * `@graph` walk translated in `JsonLd.kt` (T-136); fixtures are synthetic and
 * mirror the upstream shapes. Unlicense; see shared/core/NOTICE.md.
 */
class JsonLdTest {

    private fun nameOf(value: kotlinx.serialization.json.JsonElement?): String? =
        (value as? JsonPrimitive)?.content

    @Test
    fun blocksYieldEveryTopLevelObjectInDocumentOrder() {
        val html = """
            <html><head>
            <script type="application/ld+json">{"@context":"https://schema.org","name":"one"}</script>
            <script type='application/ld+json'>[{"name":"two"},{"name":"three"}]</script>
            <script type="application/ld+json">not json</script>
            </head></html>
        """.trimIndent()
        assertEquals(
            listOf("one", "two", "three"),
            JsonLd.objects(html).map { nameOf(it["name"]) },
        )
    }

    @Test
    fun aJsLiteralBlockRidesTheJsToJsonFallback() {
        val html =
            """<script type="application/ld+json">{name: 'loose', "@context": "https://schema.org"}</script>"""
        assertEquals(listOf("loose"), JsonLd.objects(html).map { nameOf(it["name"]) })
    }

    @Test
    fun entriesExpandAGraphAndSkipContextlessBlocks() {
        val html = """
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"VideoObject","contentUrl":"https://cdn.example/v.mp4"},
              {"@type":"BreadcrumbList"}
            ]}
            </script>
            <script type="application/ld+json">{"@type":"VideoObject","contentUrl":"https://cdn.example/ignored.mp4"}</script>
        """.trimIndent()
        assertEquals(
            listOf("https://cdn.example/v.mp4"),
            JsonLd.entries(html).mapNotNull { nameOf(it["contentUrl"]) },
        )
    }

    @Test
    fun entriesKeepNestedGraphObjectsWithoutExpandingThemAgain() {
        val html = """
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"ItemList","itemListElement":[
                {"@type":"ListItem","url":"https://cdn.example/page"}
              ]}
            ]}
            </script>
        """.trimIndent()
        assertEquals(listOf("ItemList"), JsonLd.entries(html).map { nameOf(it["@type"]) })
    }
}
