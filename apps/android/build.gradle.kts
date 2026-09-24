// Chaquopy is on the buildscript classpath unconditionally so the guarded
// typed configuration below compiles even on default checkouts; the Gradle
// plugin itself is only APPLIED when -DchaquopyVersion is set, so default
// builds never resolve CPython or pip dependencies.
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.chaquo.python:gradle:17.0.0")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)

    val chaquopyVersion = System.getProperty("chaquopyVersion")
    if (chaquopyVersion != null) {
        id("com.chaquo.python") version chaquopyVersion
    }
}

android {
    namespace = "com.anydownlod.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.anydownlod"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.0.1"
        // Chaquopy requires an explicit ABI list and only ships 64-bit
        // Python 3.11; include physical devices and x86_64 emulators.
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }
}

// Pins CPython and yt-dlp at build time; there is no in-app `yt-dlp -U`.
// Python 3.11 matches the local build machine (homebrew python3.11); a box
// with Python 3.13 can raise this. The pip wheel is Unlicense source; the
// release-executable GPL obligations do not apply (nothing bundles the
// PyInstaller binaries) - see the T-006 license inventory.
val chaquopyEnabled = plugins.hasPlugin("com.chaquo.python")
if (chaquopyEnabled) {
    extensions.configure<com.chaquo.python.ChaquopyExtension>("chaquopy") {
        defaultConfig {
            version = "3.11"
            pip {
                install("yt-dlp==2026.8.19")
            }
        }
    }
}

dependencies {
    implementation(project(":shared:ui"))
    implementation(libs.androidx.activity.compose)

    testImplementation("org.jetbrains.kotlin:kotlin-test:${libs.versions.kotlin.get()}")
    testImplementation(libs.kotlinx.coroutines.test)
}