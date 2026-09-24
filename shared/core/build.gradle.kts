import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "com.anydownlod.core"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    jvm()

    iosArm64()
    iosSimulatorArm64()

    wasmJs {
        browser()
    }

    // `java.net` transport shared by the JVM host and Android (T-056).
    // The default hierarchy (iosMain, nativeMain, webMain, ...) is applied
    // first; creating a custom source set before that would disable it.
    applyDefaultHierarchyTemplate()
    sourceSets {
        val jvmAndroidMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        // The generated EJS solver constants (see the verify/generate tasks below).
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/ejs/kotlin"))
        }

        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        jvmMain.dependencies {
            implementation(libs.zipline)
        }
        androidMain.dependencies {
            implementation(libs.zipline)
        }
        iosMain.dependencies {
            implementation(libs.zipline)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    // Opt-in live local-fixture check for the iOS NSURLSession transfer.
    // Usage: -PiosLiveFixturePort=<port> with a local HTTP server on
    // 127.0.0.1:<port>/files/tiny.bin (see IosEngineTest). Never runs by
    // default, so default CI stays fixture-only. The same opt-in flag gates
    // the extractor harness live cases (T-059).
    val liveFixturePort = providers.gradleProperty("iosLiveFixturePort").orNull
    val liveExtractorTests = providers.gradleProperty("liveExtractorTests").orNull
    val liveYoutube = providers.gradleProperty("iosLiveYouTube").orNull
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
        if (liveFixturePort != null) {
            environment("IOS_LIVE_FIXTURE_PORT", liveFixturePort)
        }
        if (liveExtractorTests != null) {
            environment("LIVE_EXTRACTOR_TESTS", liveExtractorTests)
        }
        if (liveYoutube != null) {
            environment("IOS_LIVE_YOUTUBE", liveYoutube)
        }
    }
    tasks.withType<Test>().configureEach {
        if (liveExtractorTests != null) {
            systemProperty("liveExtractorTests", liveExtractorTests)
        }
    }
}

// ---------------------------------------------------------------------------
// Bundled yt-dlp-ejs 0.8.0 (T-069)
//
// The scripts live in third_party/yt-dlp-ejs/0.8.0 with their Unlicense and the
// upstream SHA3-512 hashes copied from yt-dlp's vendor/_info.py at the pin. The
// verify task fails the build on a missing or tampered file; the generate task
// emits EjsScripts.kt from the verified minified pair. There is no runtime
// download path.
// ---------------------------------------------------------------------------
val ejsVersion = "0.8.0"
val ejsDir = rootProject.layout.projectDirectory.dir("third_party/yt-dlp-ejs/$ejsVersion")
val ejsGeneratedDir = layout.buildDirectory.dir("generated/ejs/kotlin")

fun sha3Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA3-512").digest(bytes)
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun expectedEjsHashes(hashFile: java.io.File): Map<String, String> {
    if (!hashFile.isFile) error("Missing bundled EJS hash file: ${hashFile.path}")
    return hashFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) error("Malformed EJS hash line: $line")
            parts[1].trim() to parts[0].lowercase()
        }
}

val verifyEjsBundle by tasks.registering {
    group = "verification"
    description = "Verifies the bundled yt-dlp-ejs $ejsVersion scripts against HASHES.sha512."
    doLast {
        val directory = ejsDir.asFile
        val expected = expectedEjsHashes(directory.resolve("HASHES.sha512"))
        require(expected.isNotEmpty()) { "No EJS hashes found in ${directory.path}" }
        expected.forEach { (name, hash) ->
            val file = directory.resolve(name)
            if (!file.isFile) error("Bundled EJS script is missing: ${file.path}")
            val actual = sha3Hex(file.readBytes())
            if (actual != hash) {
                error("EJS hash mismatch for $name: expected $hash, got $actual")
            }
        }
        logger.lifecycle("EJS bundle verified: ${expected.size} scripts at $ejsVersion")
    }
}

val generateEjsScripts by tasks.registering {
    group = "build"
    description = "Generates EjsScripts.kt from the verified yt-dlp-ejs minified scripts."
    dependsOn(verifyEjsBundle)
    inputs.dir(ejsDir)
    outputs.dir(ejsGeneratedDir)
    doLast {
        fun encode(text: String): String = buildString {
            append('"')
            for (character in text) {
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
            append('"')
        }
        val core = ejsDir.asFile.resolve("yt.solver.core.min.js").readText()
        val lib = ejsDir.asFile.resolve("yt.solver.lib.min.js").readText()
        val target = ejsGeneratedDir.get().asFile.resolve("com/anydownlod/core/jsc/EjsScripts.kt")
        target.parentFile.mkdirs()
        target.writeText(
            """
            |// Generated by :shared:core:generateEjsScripts from third_party/yt-dlp-ejs/$ejsVersion.
            |// Do not edit; run ./gradlew :shared:core:generateEjsScripts instead.
            |package com.anydownlod.core.jsc
            |
            |object EjsScripts {
            |    const val VERSION: String = "$ejsVersion"
            |    val core: String = ${encode(core)}
            |    val lib: String = ${encode(lib)}
            |}
            |
            """.trimMargin(),
        )
        logger.lifecycle("Generated ${target.path}")
    }
}

tasks.named("check") {
    dependsOn(verifyEjsBundle)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateEjsScripts)
}
