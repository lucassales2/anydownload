// Repository tooling (T-055): validates port/manifest.json, regenerates
// port/upstream-extractors.json from a fetched upstream copy, and rewrites the
// generated coverage block in vault/01-product/Ytdlp-equivalence.md.
//
// This module is build-time only. It is not part of any app or shared module,
// it never ships, and it does not translate or vendor upstream source.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

application {
    mainClass.set("com.anydownlod.portmanifest.MainKt")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("portManifest.root", repoRoot)
}

val repoRoot = rootProject.projectDir.absolutePath

// Default invocation: validate the manifest, then rewrite the coverage block.
tasks.named<JavaExec>("run") {
    systemProperty("portManifest.root", repoRoot)
}

// check-time gate: fails when the manifest is invalid or the generated block
// is out of date, so a ported module cannot land without a manifest entry.
tasks.register<JavaExec>("validatePortManifest") {
    group = "verification"
    description = "Validates port/manifest.json and the generated coverage block."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    systemProperty("portManifest.root", repoRoot)
    args("--check")
}

// One-off pin step. Fetch yt_dlp/extractor/_extractors.py at the pin, then:
//   ./gradlew :tools:port-manifest:generateUpstreamExtractors \
//     -PupstreamExtractorsSource=/tmp/_extractors.py
// The fetched file is not committed; only the names list, tag, and commit are.
tasks.register<JavaExec>("generateUpstreamExtractors") {
    group = "verification"
    description = "Regenerates port/upstream-extractors.json from a fetched upstream _extractors.py."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    systemProperty("portManifest.root", repoRoot)
    val source = providers.gradleProperty("upstreamExtractorsSource")
    doFirst {
        val path = source.orNull
            ?: throw GradleException(
                "Pass -PupstreamExtractorsSource=<path to a fetched yt_dlp/extractor/_extractors.py>.",
            )
        args("--upstream-source=$path")
    }
}

tasks.named("check") {
    dependsOn("validatePortManifest")
}
