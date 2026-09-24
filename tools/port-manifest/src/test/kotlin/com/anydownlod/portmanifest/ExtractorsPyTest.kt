package com.anydownlod.portmanifest

import kotlin.test.Test
import kotlin.test.assertEquals

class ExtractorsPyTest {
    @Test
    fun parsesBlockAndSingleLineImportsInSourceOrder() {
        val source = """
            # flake8: noqa: F401
            from .abc import (
                ABCIE,
                ABCIViewIE,
            )
            from .acast import ACastChannelIE
            from .adn import (
                ADNIE,
                ADNSeasonIE
            )
            from .x import OneIE, TwoIE
        """.trimIndent()
        assertEquals(
            listOf("ABCIE", "ABCIViewIE", "ACastChannelIE", "ADNIE", "ADNSeasonIE", "OneIE", "TwoIE"),
            ExtractorsPy.parseNames(source),
        )
    }

    @Test
    fun deduplicatesRepeatedNames() {
        val source = """
            from .a import (
                AlphaIE,
            )
            from .b import AlphaIE
        """.trimIndent()
        assertEquals(listOf("AlphaIE"), ExtractorsPy.parseNames(source))
    }

    @Test
    fun ignoresUnrelatedLinesAndComments() {
        val source = """
            # from .commented import CommentedIE
            import os
            X = 1
            # from .commented import CommentedIE
        """.trimIndent()
        assertEquals(emptyList(), ExtractorsPy.parseNames(source))
    }

    @Test
    fun parsedUpstreamFixtureSlicesCleanly() {
        val source = """
            from .youtube import (
                YoutubeIE,
                YoutubeTabIE,
            )
            from .zap import (
                ZapIE,
            )
        """.trimIndent()
        val names = ExtractorsPy.parseNames(source).sorted()
        assertEquals(listOf("YoutubeIE", "YoutubeTabIE", "ZapIE"), names)
    }
}
