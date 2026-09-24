package com.anydownlod.core.extract.harness

/**
 * JVM fixture store backed by the test classpath. Recorded fixtures live under
 * `shared/core/src/commonTest/resources/fixtures/<extractor>/`, which Gradle
 * puts on the JVM test classpath.
 */
object ClasspathFixtureStore : FixtureStore {
    override fun read(path: String): String? =
        object {}.javaClass.classLoader?.getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
}
