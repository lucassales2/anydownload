@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

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
    testImplementation(compose.desktop.currentOs)
    testImplementation(compose.uiTest)
}

// Live yt-dlp checks are opt-in through -D properties so no media URL lives in the repo.
tasks.withType<Test>().configureEach {
    // UI tests assert English copy. A host locale such as pt-BR must not
    // change those lookups while values-pt-rBR is present.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    // The T-062 differential oracle runs only with -PytDlpOracle=true.
    providers.gradleProperty("ytDlpOracle").orNull?.let { value ->
        systemProperty("ytDlpOracle", value)
    }
    // T-064's live Kotlin download check runs only with -PliveExtractorTests=true.
    providers.gradleProperty("liveExtractorTests").orNull?.let { value ->
        systemProperty("liveExtractorTests", value)
    }
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
            // jpackage builds a .dmg only for the Mac this task runs on, and an
            // .exe only on Windows. packageDesktopReleases cross-builds the
            // other desktop artifacts from this machine.
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi)
            packageName = "AnyDownload"
            packageVersion = "0.1.0"
            description = "Download video and audio with a local yt-dlp."
            vendor = "Lucas Sales"
            copyright = "© 2026 Lucas Sales. MIT License."
            includeAllModules = true

            macOS {
                bundleID = "com.anydownlod.desktop"
                // jpackage rejects a macOS app version whose first number is 0.
                packageVersion = "1.0.0"
            }

            windows {
                console = false
                menuGroup = "AnyDownload"
                // Stable id so a later install upgrades this one.
                upgradeUuid = "6f0c2e4a-8b31-4d57-9a6e-1c5b7d0e2f48"
                dirChooser = true
                perUserInstall = true
                shortcut = true
            }
        }
    }
}

// Uber jar plus the Windows Skiko DLL. packageUberJarForCurrentOS only embeds
// the host OS natives, so a Mac build would not start on Windows without this.
tasks.register<Jar>("windowsUberJar") {
    group = "compose desktop"
    description = "Fat jar that includes the Windows x64 Skiko runtime."
    dependsOn("packageUberJarForCurrentOS")
    archiveBaseName.set("AnyDownload")
    archiveVersion.set("0.1.0")
    archiveClassifier.set("windows-x64")
    destinationDirectory.set(layout.buildDirectory.dir("windows"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.anydownlod.desktop.MainKt"
    }
    from({
        zipTree(producedJar("packageUberJarForCurrentOS"))
    })
    from({
        val version = configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .first { it.name.startsWith("skiko-awt-runtime-") }
            .moduleVersion.id.version
        val windowsSkiko = configurations.detachedConfiguration(
            dependencies.create("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:$version"),
        )
        windowsSkiko.resolve().map { zipTree(it) }
    })
    doLast {
        val dlls = zipTree(archiveFile.get().asFile).matching { include("**/*.dll") }.files
        check(dlls.isNotEmpty()) {
            "Windows Skiko DLL is missing from ${archiveFile.get().asFile}"
        }
    }
}

// Cross-built Windows app: AnyDownload.exe plus a bundled Temurin JRE, and an
// NSIS installer. The app still expects yt-dlp and ffmpeg on the user's PATH
// and does not bundle them.
tasks.register("packageWindowsExe") {
    group = "compose desktop"
    description = "Build AnyDownload-Setup.exe and a portable Windows folder."
    dependsOn("windowsUberJar")
    val imageDir = layout.buildDirectory.dir("windows/image")
    val setupExe = layout.buildDirectory.file("windows/AnyDownload-Setup.exe")
    val launcherSource = layout.projectDirectory.file("packaging/windows/launcher.c")
    val nsisTemplate = layout.projectDirectory.file("packaging/windows/installer.nsi")
    inputs.file(launcherSource)
    inputs.file(nsisTemplate)
    inputs.files(tasks.named("windowsUberJar"))
    outputs.file(setupExe)
    outputs.dir(imageDir)

    doLast {
        val image = imageDir.get().asFile
        image.deleteRecursively()
        val appDir = image.resolve("app")
        val jreDir = image.resolve("jre")
        appDir.mkdirs()

        val uber = producedJar("windowsUberJar")
        uber.copyTo(appDir.resolve("AnyDownload.jar"), overwrite = true)

        val cache = layout.buildDirectory.dir("windows/cache").get().asFile
        cache.mkdirs()
        val jreZip = cache.resolve("temurin-jre-windows-x64.zip")
        downloadTemurinWindowsJre(jreZip)
        val extracted = cache.resolve("jre-extracted")
        extracted.deleteRecursively()
        project.copy {
            from(project.zipTree(jreZip))
            into(extracted)
        }
        val javaw = extracted.walkTopDown().firstOrNull { it.name.equals("javaw.exe", ignoreCase = true) }
            ?: error("The Temurin JRE zip has no javaw.exe")
        val jreRoot = javaw.parentFile.parentFile
        jreRoot.copyRecursively(jreDir, overwrite = true)

        val exe = image.resolve("AnyDownload.exe")
        compileWindowsLauncher(launcherSource.asFile, exe)

        val script = cache.resolve("installer.nsi")
        val nsis = nsisTemplate.asFile.readText()
            .replace("@IMAGE@", image.absolutePath.replace("\\", "/"))
            .replace("@OUT@", setupExe.get().asFile.absolutePath.replace("\\", "/"))
        script.writeText(nsis)
        runCommand(windowsTool("makensis"), script.absolutePath)
        check(setupExe.get().asFile.isFile) { "NSIS did not write ${setupExe.get().asFile}" }
        logger.lifecycle("Windows installer: ${setupExe.get().asFile.absolutePath}")
        logger.lifecycle("Portable app: ${image.absolutePath}")
    }
}

// Intel Mac image. jpackage on Apple Silicon only emits an arm64 runtime, so
// this bundles a Temurin macOS x64 JRE around the same jar.
tasks.register("packageMacX64Dmg") {
    group = "compose desktop"
    description = "Build an Intel macOS disk image."
    dependsOn("windowsUberJar")
    val dmg = layout.buildDirectory.file("macos/AnyDownload-0.1.0-macos-x64.dmg")
    val launcherSource = layout.projectDirectory.file("packaging/macos/AnyDownload.command")
    val plistSource = layout.projectDirectory.file("packaging/macos/Info.plist")
    inputs.file(launcherSource)
    inputs.file(plistSource)
    inputs.files(tasks.named("windowsUberJar"))
    outputs.file(dmg)

    doLast {
        val work = layout.buildDirectory.dir("macos/x64").get().asFile
        work.deleteRecursively()
        val app = work.resolve("AnyDownload.app")
        val contents = app.resolve("Contents")
        val macOs = contents.resolve("MacOS")
        val appDir = contents.resolve("app")
        macOs.mkdirs()
        appDir.mkdirs()

        val uber = producedJar("windowsUberJar")
        val natives = project.zipTree(uber).matching { include("**/libskiko-macos-x64.dylib") }.files
        check(natives.isNotEmpty()) { "Intel Skiko library is missing from $uber" }
        uber.copyTo(appDir.resolve("AnyDownload.jar"), overwrite = true)

        val cache = layout.buildDirectory.dir("macos/cache").get().asFile
        cache.mkdirs()
        val jreArchive = cache.resolve("temurin-jre-macos-x64.tar.gz")
        downloadTemurinJre(jreArchive, os = "mac", arch = "x64")
        val extracted = cache.resolve("jre-extracted-x64")
        extracted.deleteRecursively()
        project.copy {
            from(project.tarTree(project.resources.gzip(jreArchive)))
            into(extracted)
        }
        val java = extracted.walkTopDown().firstOrNull {
            it.name == "java" && it.parentFile.name == "bin" && it.parentFile.parentFile.name == "Home"
        } ?: error("The Temurin macOS JRE archive has no Contents/Home/bin/java")
        val jreRoot = java.parentFile.parentFile.parentFile.parentFile
        jreRoot.copyRecursively(contents.resolve("runtime"), overwrite = true)

        launcherSource.asFile.copyTo(macOs.resolve("AnyDownload"), overwrite = true)
        macOs.resolve("AnyDownload").setExecutable(true)
        val plist = plistSource.asFile.readText().replace("@VERSION@", "1.0.0")
        contents.resolve("Info.plist").writeText(plist)

        val stage = work.resolve("dmg-stage")
        stage.mkdirs()
        app.copyRecursively(stage.resolve("AnyDownload.app"), overwrite = true)
        runCommand("ln", "-sfn", "/Applications", stage.resolve("Applications").absolutePath)
        val output = dmg.get().asFile
        output.parentFile.mkdirs()
        runCommand(
            "hdiutil",
            "create",
            "-volname",
            "AnyDownload",
            "-srcfolder",
            stage.absolutePath,
            "-ov",
            "-format",
            "UDZO",
            output.absolutePath,
        )
        logger.lifecycle("Intel macOS disk image: ${output.absolutePath}")
    }
}

tasks.register("packageDesktopReleases") {
    group = "compose desktop"
    description = "Collect Windows x64, Mac Intel, and Mac Apple Silicon release files."
    dependsOn("packageWindowsExe", "packageDmg", "packageMacX64Dmg")
    val releaseDir = layout.buildDirectory.dir("release")
    outputs.dir(releaseDir)

    doLast {
        val release = releaseDir.get().asFile
        release.mkdirs()
        val windows = layout.buildDirectory.file("windows/AnyDownload-Setup.exe").get().asFile
        val intel = layout.buildDirectory.file("macos/AnyDownload-0.1.0-macos-x64.dmg").get().asFile
        val siliconDir = layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile
        val silicon = siliconDir.listFiles()?.singleOrNull { it.extension == "dmg" }
            ?: error("Apple Silicon disk image was not written to $siliconDir")
        windows.copyTo(release.resolve("AnyDownload-0.1.0-windows-x64-setup.exe"), overwrite = true)
        intel.copyTo(release.resolve("AnyDownload-0.1.0-macos-x64.dmg"), overwrite = true)
        silicon.copyTo(release.resolve("AnyDownload-0.1.0-macos-aarch64.dmg"), overwrite = true)
        val sums = release.resolve("SHA256SUMS")
        sums.writeText(
            release.listFiles().orEmpty()
                .filter { it.isFile && it.name != "SHA256SUMS" }
                .sortedBy { it.name }
                .joinToString("\n") { "${sha256(it)}  ${it.name}" } + "\n",
        )
        logger.lifecycle("Release files: ${release.absolutePath}")
    }
}

fun downloadTemurinWindowsJre(destination: File) =
    downloadTemurinJre(destination, os = "windows", arch = "x64")

@Suppress("UNCHECKED_CAST")
fun downloadTemurinJre(destination: File, os: String, arch: String) {
    val api = "https://api.adoptium.net/v3/assets/latest/21/hotspot" +
        "?architecture=$arch&image_type=jre&os=$os&vendor=eclipse"
    val releases = groovy.json.JsonSlurper().parse(URI(api).toURL()) as List<Map<String, Any?>>
    val binary = releases.first()["binary"] as Map<String, Any?>
    val pkg = binary["package"] as Map<String, Any?>
    val link = pkg["link"] as String
    val checksum = (pkg["checksum"] as String).lowercase()
    if (destination.isFile && sha256(destination) == checksum) return
    destination.outputStream().use { out ->
        URI(link).toURL().openStream().use { input -> input.copyTo(out) }
    }
    val actual = sha256(destination)
    check(actual == checksum) { "Temurin JRE checksum mismatch: expected $checksum, got $actual" }
}

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun compileWindowsLauncher(source: File, exe: File) {
    runCommand(
        windowsTool("x86_64-w64-mingw32-gcc"),
        "-O2",
        "-municode",
        "-mwindows",
        "-static",
        "-static-libgcc",
        "-o",
        exe.absolutePath,
        source.absolutePath,
    )
}

fun producedJar(taskName: String): File =
    tasks.named(taskName).get().outputs.files.files.single { it.extension == "jar" }

fun runCommand(vararg command: String) {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    val code = process.waitFor()
    check(code == 0) { "${command.first()} exited with $code\n$output" }
}

fun windowsTool(name: String): String {
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    val found = path.map { File(it, name) }.firstOrNull { it.canExecute() }
    if (found != null) return found.absolutePath
    val homebrew = File("/opt/homebrew/bin/$name")
    if (homebrew.canExecute()) return homebrew.absolutePath
    error(
        "$name was not found. Install it with `brew install mingw-w64 makensis` " +
            "before running packageWindowsExe.",
    )
}
