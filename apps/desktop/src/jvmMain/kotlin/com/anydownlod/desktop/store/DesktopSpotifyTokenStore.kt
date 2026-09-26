package com.anydownlod.desktop.store

import com.anydownlod.core.music.SpotifyTokenStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Desktop [SpotifyTokenStore] over a file in the app state directory. The
 * token is written with owner-only permissions where the filesystem supports
 * them; it is never logged, shown, or copied into a fixture.
 */
class DesktopSpotifyTokenStore(private val stateDirectory: Path) : SpotifyTokenStore {

    private val file: Path = stateDirectory.resolve("spotify-auth.token")

    override fun load(): String? = runCatching {
        if (!Files.isRegularFile(file)) return null
        Files.readString(file).trim().takeIf { it.isNotEmpty() }
    }.getOrNull()

    override fun save(token: String) {
        Files.createDirectories(file.parent)
        Files.writeString(file, token)
        runCatching {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        }
    }

    override fun clear() {
        runCatching { Files.deleteIfExists(file) }
    }
}
