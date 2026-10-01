/*
 * Desktop cookie jar source — AnyDownload (T-018)
 *
 * Resolves the stored `cookies.txt` for one job. The engine calls this once
 * per job with `useCookies`; the parsed jar is a snapshot, so replacing or
 * deleting the file during the job cannot change what an in-flight request
 * sends. The path never appears in a job, a log, or diagnostics.
 */
package com.anydownload.desktop.cookies

import com.anydownload.core.cookies.CookieJar
import com.anydownload.core.cookies.CookieJarSource
import com.anydownload.desktop.store.DesktopCookieStore
import java.nio.file.Files
import java.nio.file.Path

class DesktopCookieJarSource(private val store: DesktopCookieStore) : CookieJarSource {
    override suspend fun currentJar(): CookieJar? {
        val path = store.storedFilePath() ?: return null
        val text = runCatching { Files.readString(Path.of(path)) }.getOrNull() ?: return null
        return CookieJar.fromText(text)
    }
}
