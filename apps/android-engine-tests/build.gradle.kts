// JVM-equivalent tests for the Android engine code (T-041).
//
// These tests compile the *pure* engine sources (package
// `com.anydownlod.android.engine`, which has no Android/Compose imports) on
// the JVM and run them with the local-JVM fakes from the same sources.
// Nothing here imports Python or Chaquopy.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())

    sourceSets {
        main {
            kotlin.srcDir("../android/src/main/kotlin/com/anydownlod/android/engine")
        }
        test {
            kotlin.srcDir("../android/src/test/kotlin")
        }
    }
}

dependencies {
    implementation(project(":shared:core"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
    // Local HTTP fixture servers bind loopback; keep assertions on English.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}