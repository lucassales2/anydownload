package com.anydownlod.ui.queue

import com.anydownlod.core.domain.DownloadJob
import com.anydownlod.core.domain.JobState
import com.anydownlod.core.fake.InMemoryDownloadEngine
import com.anydownlod.ui.export.JobSourceUrls
import com.anydownlod.ui.generated.resources.Res
import com.anydownlod.ui.generated.resources.copied_urls
import com.anydownlod.ui.i18n.UiText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * T-122: the queue rules now live on [QueueViewModel]. This test runs the
 * real ViewModel over [InMemoryDownloadEngine] with the main dispatcher
 * replaced, so it needs no Compose rule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueuePresenterTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun engine() = InMemoryDownloadEngine(seedJobs = InMemoryDownloadEngine.sampleJobs())

    private fun List<DownloadJob>.byId(id: String): DownloadJob = first { it.id == id }

    @Test
    fun rowsKeepOnlyQueueStates() {
        val state = QueueViewModel(engine()).state.value

        assertEquals(
            setOf(
                "seed-pending",
                "seed-scheduled",
                "seed-downloading",
                "seed-downloading-unknown-total",
                "seed-postprocessing",
            ),
            state.rows.map { it.id }.toSet(),
        )
        assertTrue(state.rows.none { it.state.isTerminal })
        assertEquals(3, state.working)
        assertEquals(2, state.notStarted)
    }

    @Test
    fun nullPercentStaysIndeterminateAndOmitsSpeedAndEta() {
        val row = QueueViewModel(engine()).state.value.rows
            .first { it.id == "seed-downloading-unknown-total" }

        assertNull(row.percent)
        assertTrue(row.indeterminate)
        assertNull(row.speedBytesPerSecond)
        assertNull(row.etaSeconds)
    }

    @Test
    fun knownPercentKeepsSpeedAndEta() {
        val row = QueueViewModel(engine()).state.value.rows
            .first { it.id == "seed-downloading" }

        assertEquals(42.5, row.percent)
        assertFalse(row.indeterminate)
        assertEquals(1_200_000.0, row.speedBytesPerSecond)
        assertEquals(5L, row.etaSeconds)
    }

    @Test
    fun unknownStateIsNotAQueueRow() {
        val unknown = InMemoryDownloadEngine.sampleJobs().first()
            .copy(id = "future", state = JobState.UNKNOWN)
        val viewModel = QueueViewModel(InMemoryDownloadEngine(seedJobs = listOf(unknown)))

        assertTrue(viewModel.state.value.rows.isEmpty())
    }

    @Test
    fun startSelectedSkipsRowsAlreadyWorking() {
        val engine = engine()
        val viewModel = QueueViewModel(engine)

        viewModel.toggle("seed-pending")
        viewModel.toggle("seed-downloading")
        viewModel.startSelected()

        assertEquals(JobState.QUEUED, engine.jobs.value.byId("seed-pending").state)
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.byId("seed-downloading").state)
    }

    @Test
    fun requestCancelSelectedCancelsRowsThatDoNotNeedConfirmation() {
        val engine = engine()
        val viewModel = QueueViewModel(engine)

        viewModel.toggle("seed-pending")
        viewModel.requestCancelSelected()

        assertEquals(JobState.CANCELLED, engine.jobs.value.byId("seed-pending").state)
        assertTrue(viewModel.state.value.pendingCancelIds.isEmpty())
        assertTrue(viewModel.state.value.selectedIds.isEmpty())
    }

    @Test
    fun requestCancelSelectedAsksBeforeCancellingWorkingRows() {
        val engine = engine()
        val viewModel = QueueViewModel(engine)

        viewModel.toggle("seed-downloading")
        viewModel.requestCancelSelected()

        assertEquals(setOf("seed-downloading"), viewModel.state.value.pendingCancelIds)
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.byId("seed-downloading").state)

        viewModel.dismissCancel()

        assertTrue(viewModel.state.value.pendingCancelIds.isEmpty())
        assertEquals(JobState.DOWNLOADING, engine.jobs.value.byId("seed-downloading").state)

        viewModel.requestCancelSelected()
        viewModel.confirmCancel()

        assertEquals(JobState.CANCELLED, engine.jobs.value.byId("seed-downloading").state)
        assertTrue(viewModel.state.value.pendingCancelIds.isEmpty())
    }

    @Test
    fun bulkActionsOperateOnKnownIdsOnlyAndDoNotRollBack() {
        val engine = engine()
        val viewModel = QueueViewModel(engine)

        viewModel.toggle("seed-pending")
        viewModel.toggle("missing-id")
        viewModel.startSelected()

        assertEquals(JobState.QUEUED, engine.jobs.value.byId("seed-pending").state)
        assertEquals(JobState.SCHEDULED, engine.jobs.value.byId("seed-scheduled").state)

        viewModel.requestCancelSelected(setOf("seed-downloading", "missing-id", "seed-completed"))
        viewModel.confirmCancel()

        assertEquals(JobState.CANCELLED, engine.jobs.value.byId("seed-downloading").state)
        assertEquals(JobState.COMPLETED, engine.jobs.value.byId("seed-completed").state)
    }

    @Test
    fun toggleAndToggleAllManageSelection() {
        val viewModel = QueueViewModel(engine())

        viewModel.toggle("seed-pending")
        assertEquals(setOf("seed-pending"), viewModel.state.value.selectedIds)

        viewModel.toggleAll()
        assertEquals(viewModel.state.value.rows.map { it.id }.toSet(), viewModel.state.value.selectedIds)

        viewModel.toggleAll()
        assertTrue(viewModel.state.value.selectedIds.isEmpty())
    }

    @Test
    fun selectionAndPendingCancelArePrunedWhenARowLeavesTheQueue() {
        val engine = engine()
        val viewModel = QueueViewModel(engine)

        viewModel.toggle("seed-pending")
        viewModel.toggle("seed-downloading")
        viewModel.requestCancelSelected(setOf("seed-downloading"))
        assertEquals(setOf("seed-downloading"), viewModel.state.value.pendingCancelIds)

        engine.cancel("seed-pending")
        engine.cancel("seed-downloading")

        assertTrue(viewModel.state.value.selectedIds.isEmpty())
        assertTrue(viewModel.state.value.pendingCancelIds.isEmpty())
    }

    private fun batchEngine(): InMemoryDownloadEngine {
        val base = InMemoryDownloadEngine.sampleJobs().first { it.id == "seed-completed" }
        fun child(id: String, state: JobState, batch: String? = "batch-a") = base.copy(
            id = id,
            state = state,
            parentBatchId = batch,
            request = base.request.copy(sourceUrl = "https://example.com/watch?v=$id"),
        )
        return InMemoryDownloadEngine(
            seedJobs = listOf(
                child("batch-1", JobState.PENDING),
                child("batch-2", JobState.COMPLETED),
                child("batch-3", JobState.FAILED),
                child("other-1", JobState.PENDING, batch = null),
            ),
        )
    }

    @Test
    fun copySelectedReturnsTheSelectedQueueUrlsAndBatchCopyIncludesEveryChild() {
        val viewModel = QueueViewModel(batchEngine())

        viewModel.toggle("batch-1")

        assertEquals(
            listOf("https://example.com/watch?v=batch-1"),
            viewModel.copySelected(),
        )
        assertEquals(
            listOf(
                "https://example.com/watch?v=batch-1",
                "https://example.com/watch?v=batch-2",
                "https://example.com/watch?v=batch-3",
            ),
            viewModel.copyBatch(),
        )
        assertTrue(viewModel.state.value.batchCopyEnabled)

        val text = JobSourceUrls.text(viewModel.copyBatch())
        assertTrue(text.none { it == '\r' || it == '\u0000' })
        for (forbidden in listOf("cookie", "signature", "googlevideo", "token=")) {
            assertTrue(forbidden !in text, "copied text must not carry '$forbidden'")
        }
    }

    @Test
    fun batchCopyIsDisabledWhenNoSelectedRowBelongsToABatch() {
        val viewModel = QueueViewModel(batchEngine())

        viewModel.toggle("other-1")

        assertFalse(viewModel.state.value.batchCopyEnabled)
        assertTrue(viewModel.copyBatch().isEmpty())
    }

    @Test
    fun noteCopiedSetsTheStatusMessage() {
        val viewModel = QueueViewModel(engine())

        viewModel.noteCopied(3)

        assertEquals(UiText.of(Res.string.copied_urls, 3), viewModel.state.value.statusMessage)
    }
}
