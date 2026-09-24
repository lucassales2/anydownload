package com.anydownlod.core.engine

import com.anydownlod.core.engine.UrlClassifier.Classification
import kotlin.test.Test
import kotlin.test.assertEquals

class UrlClassifierTest {

    @Test
    fun htmlIsNeedsExtractor() {
        assertEquals(Classification.NEEDS_EXTRACTOR, UrlClassifier.classify("text/html"))
        assertEquals(Classification.NEEDS_EXTRACTOR, UrlClassifier.classify("text/html; charset=utf-8"))
        assertEquals(Classification.NEEDS_EXTRACTOR, UrlClassifier.classify("Text/HTML"))
        assertEquals(Classification.NEEDS_EXTRACTOR, UrlClassifier.classify("application/xhtml+xml"))
    }

    @Test
    fun mediaAndUnknownBodiesAreDirectFiles() {
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify("video/mp4"))
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify("video/webm"))
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify("audio/mpeg"))
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify("application/octet-stream"))
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify("text/plain"))
        assertEquals(Classification.DIRECT_FILE, UrlClassifier.classify(null))
    }
}