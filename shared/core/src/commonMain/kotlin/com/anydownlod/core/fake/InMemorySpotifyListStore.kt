package com.anydownlod.core.fake

import com.anydownlod.core.music.SpotifyListStore

/** In-memory [SpotifyListStore] for tests and fake hosts. */
class InMemorySpotifyListStore : SpotifyListStore {

    /** Relative path to file content, in insertion order. */
    val files: MutableMap<String, String> = linkedMapOf()

    override fun exists(relativePath: String): Boolean = files.containsKey(relativePath)

    override fun readLines(relativePath: String): List<String>? =
        files[relativePath]?.lines()?.filter { it.isNotBlank() }

    override fun write(relativePath: String, content: String): Boolean {
        files[relativePath] = content
        return true
    }

    override fun delete(relativePath: String): Boolean = files.remove(relativePath) != null

    override fun list(): List<String> = files.keys.toList()
}
