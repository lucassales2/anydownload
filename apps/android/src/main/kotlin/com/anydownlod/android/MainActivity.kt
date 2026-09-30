package com.anydownlod.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.anydownlod.core.SharedLinkInbox
import com.anydownlod.core.cookies.NetscapeCookieFile
import com.anydownlod.ui.App
import dev.zacsweers.metro.createGraphFactory
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

class MainActivity : ComponentActivity() {

    private var cookiePickContinuation: CancellableContinuation<String?>? = null

    /** T-020: a share intent that arrived before or after the graph existed. */
    private var pendingSharedText: String? = null
    private var activeSharedLinkInbox: SharedLinkInbox? = null

    private val pickCookieDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val continuation = cookiePickContinuation
        cookiePickContinuation = null
        continuation?.resume(copyPickedCookie(uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingSharedText = sharedTextFrom(intent)
        setContent {
            // The real Android graph: shared HTTP engine for direct files and
            // the Chaquopy adapter for other URLs. remember keeps one graph
            // (and its engine scope) for the activity lifetime.
            val graph = remember {
                createGraphFactory<AndroidAppGraph.Factory>().create(
                    applicationContext,
                    cookiePicker = AndroidCookiePickerBridge { pickCookieFile() },
                    sharedLinkInbox = SharedLinkInbox().also { activeSharedLinkInbox = it },
                )
            }
            LaunchedEffect(Unit) {
                pendingSharedText?.let { text ->
                    graph.sharedLinkInbox?.offer(text)
                    pendingSharedText = null
                }
            }
            App(graph = graph)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val text = sharedTextFrom(intent) ?: return
        val inbox = activeSharedLinkInbox
        if (inbox != null) inbox.offer(text) else pendingSharedText = text
    }

    /** The text of a `SEND text/plain` share, or null for any other intent. */
    private fun sharedTextFrom(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null

    /**
     * The system picker is async; the Settings action awaits this suspend
     * call. A cancel resumes null and the UI reports "not available".
     */
    private suspend fun pickCookieFile(): String? = suspendCancellableCoroutine { continuation ->
        cookiePickContinuation = continuation
        continuation.invokeOnCancellation { cookiePickContinuation = null }
        pickCookieDocument.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
    }

    /**
     * Copies the chosen document into the app cache so the pure store can
     * validate it. The store removes the copy after the import, whether the
     * file is accepted or rejected. The copy stops at the shared 1 MiB cap,
     * so an oversized document is rejected without buffering it whole.
     */
    private fun copyPickedCookie(uri: Uri?): String? {
        if (uri == null) return null
        return runCatching {
            val target = File(cacheDir, "cookie-import.txt")
            val input = contentResolver.openInputStream(uri) ?: return null
            input.use { source ->
                target.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > NetscapeCookieFile.MAX_BYTES + 1) break
                        sink.write(buffer, 0, read)
                    }
                }
            }
            target.absolutePath
        }.getOrNull()
    }
}
