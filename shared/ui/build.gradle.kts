@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidLibrary {
        namespace = "com.anydownlod.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Compose resources are read through the generated Res class on Android too.
        androidResources.enable = true
    }

    jvm()

    // Compose Multiplatform 1.12 no longer publishes an Intel-simulator (iosX64) variant.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "AnyDownloadKit"
            isStatic = true
        }
    }

    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // api so platform hosts can use the graph and domain types directly.
            api(project(":shared:core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(compose.components.resources)
        }
        jvmTest.dependencies {
            // Headless shell click-through; no desktop window or OS input needed.
            implementation(compose.uiTest)
            implementation(compose.desktop.currentOs)
            implementation(kotlin("test"))
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.anydownlod.ui.generated.resources"
}

// UI tests assert the English copy. A host locale such as pt-BR must not
// change those lookups while values-pt-rBR is present.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
}
