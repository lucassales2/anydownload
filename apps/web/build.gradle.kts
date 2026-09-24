plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":shared:ui"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
