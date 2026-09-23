plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    // The scaffold keeps the desktop entry point under src/jvmMain/kotlin and
    // the JVM tests under src/jvmTest/kotlin. The Kotlin JVM plugin defaults to
    // src/main/kotlin and src/test/kotlin, so register both directories.
    sourceSets {
        main {
            kotlin.srcDir("src/jvmMain/kotlin")
        }
        test {
            kotlin.srcDir("src/jvmTest/kotlin")
            resources.srcDir("src/jvmTest/resources")
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

// Live yt-dlp checks are opt-in through -D properties so no media URL lives in the repo.
tasks.withType<Test>().configureEach {
    listOf(
        "anydownlod.live.url",
        "anydownlod.live.cancelUrl",
        "anydownlod.live.failureUrl",
        "anydownlod.live.audioUrl",
        "anydownlod.live.playlistUrl",
        "anydownlod.live.batchUrl",
        "anydownlod.live.thumbnailUrl",
        "anydownlod.live.subscriptionUrl",
        "anydownlod.live.root",
    ).forEach { key ->
        System.getProperty(key)?.let { value -> systemProperty(key, value) }
    }
}

compose.desktop {
    application {
        mainClass = "com.anydownlod.desktop.MainKt"

        nativeDistributions {
            packageName = "AnyDownload"
            packageVersion = "0.1.0"
        }
    }
}
