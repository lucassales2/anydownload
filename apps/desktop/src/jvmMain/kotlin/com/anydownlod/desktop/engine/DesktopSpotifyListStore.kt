package com.anydownlod.desktop.engine

import com.anydownlod.core.music.SpotifyListStore
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop [SpotifyListStore] over the download root. Paths are normalized and
 * confined to the root; a path that escapes it is refused, never read, and
 * never written. The root is re-read on every call so a Settings change is
 * honored without rebuilding the service.
 */
class DesktopSpotifyListStore(private val rootProvider: () -> String) : SpotifyListStore {

    override fun exists(relativePath: String): Boolean =
        runCatching { Files.isRegularFile(resolve(relativePath)) }.getOrDefault(false)

    override fun readLines(relativePath: String): List<String>? = runCatching {
        val path = resolve(relativePath)
        if (!Files.isRegularFile(path)) null else Files.readAllLines(path)
    }.getOrNull()

    override fun write(relativePath: String, content: String): Boolean = runCatching {
        val path = resolve(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        true
    }.getOrDefault(false)

    override fun list(): List<String> = runCatching {
        val root = Path.of(rootProvider()).toAbsolutePath().normalize()
        if (!Files.isDirectory(root)) return emptyList()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .toList()
        }
    }.getOrDefault(emptyList())

    override fun delete(relativePath: String): Boolean =
        runCatching { Files.deleteIfExists(resolve(relativePath)) }.getOrDefault(false)

    private fun resolve(relativePath: String): Path {
        require(relativePath.isNotBlank()) { "The list file path is empty." }
        val root = Path.of(rootProvider()).toAbsolutePath().normalize()
        val resolved = root.resolve(relativePath).normalize()
        require(resolved != root && resolved.startsWith(root)) {
            "The list file path escapes the download root."
        }
        return resolved
    }
}
