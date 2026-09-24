plugins {
    alias(libs.plugins.kotlin.multiplatform)
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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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
    // default, so default CI stays fixture-only.
    val liveFixturePort = providers.gradleProperty("iosLiveFixturePort").orNull
    tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
        if (liveFixturePort != null) {
            environment("IOS_LIVE_FIXTURE_PORT", liveFixturePort)
        }
    }
}
