pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // The Kotlin/JS and Kotlin/Wasm toolchain declares project-level download
    // repositories at configuration time. FAIL_ON_PROJECT_REPOS would reject the
    // build and PREFER_PROJECT would hide the repositories below, so resolution
    // stays centralized here with those toolchain downloads declared explicitly.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()

        // The Kotlin/JS and Kotlin/Wasm toolchain downloads Node and Yarn from
        // project-level repositories that FAIL_ON_PROJECT_REPOS would reject.
        // Declaring them here keeps the policy while allowing npm installs.
        ivy("https://nodejs.org/dist") {
            name = "Node Distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
                artifact("v[revision]/[artifact]-v[revision]-[classifier].[ext]")
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn Distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen Distributions"
            patternLayout {
                artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]")
                artifact("version_[revision]/[artifact]-version_[revision].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
    }
}

rootProject.name = "anydownlod"

// Shared Kotlin Multiplatform modules.
include(":shared:core")
include(":shared:network")
include(":shared:ui")

// Platform entry points. The iOS host lives in apps/ios as an Xcode project, not a Gradle module.
include(":apps:android")
include(":apps:desktop")
include(":apps:web")
