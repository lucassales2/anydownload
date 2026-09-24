package com.anydownlod.core.extract.harness

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClasspathFixtureStoreTest {

    @Test
    fun readsARecordedFixtureFromTheTestClasspath() {
        assertEquals(
            """{"fixture":"self","value":1}""",
            ClasspathFixtureStore.read("fixtures/self/hello.json")?.trim(),
        )
    }

    @Test
    fun aMissingFixtureIsNull() {
        assertNull(ClasspathFixtureStore.read("fixtures/self/missing.json"))
    }
}
