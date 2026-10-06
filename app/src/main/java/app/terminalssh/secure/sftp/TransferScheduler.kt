package app.terminalssh.secure.sftp

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** One persistent actor owns dispatch. Worker completion always wakes it, even after failure. */
internal class TransferScheduler(
    scope: CoroutineScope,
    private val queue: TransferQueue,
    private val mayStart: () -> Boolean = { true },
    private val adaptConcurrency: Boolean = true,
    private val retryDelayMillis: (Int) -> Long = { attempt ->
        1_000L * (1L shl (attempt - 1).coerceIn(0, 5))
    },
    private val execute: suspend (Transfer) -> Unit,
) {
    private sealed interface Event {
        data object Wake : Event
        data class Finished(val id: String) : Event
    }
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val wakePending = AtomicBoolean(false)
    private val job: Job = scope.launch {
        // Actual workers, not RUNNING rows: a paused worker still owns its channel until exit.
        val inFlight = mutableSetOf<String>()
        // Monotonic deadlines: wall-clock/timezone changes cannot accelerate a retry.
        val retryAt = mutableMapOf<String, Long>()
        val observer = launch { queue.transfers.collect { wake() } }
        val limitObserver = launch { queue.concurrency.collect { wake() } }
        var retryTimer: Job? = null
        try {
            for (event in events) {
                if (event is Event.Wake) wakePending.set(false)
                retryTimer?.cancel()
                if (event is Event.Finished) {
                    inFlight.remove(event.id)
                    queue.transfers.value.firstOrNull { it.id == event.id }?.let { transfer ->
                        if (transfer.state == TransferState.QUEUED && transfer.errorKind?.isRetriable == true) {
                            retryAt[event.id] = nowMillis() + retryDelayMillis(transfer.attempts).coerceAtLeast(0)
                        }
                    }
                }
                // Manual resume clears the error and must also clear automatic backoff.
                val pendingRetries = queue.transfers.value.filter {
                    it.state == TransferState.QUEUED && it.errorKind?.isRetriable == true
                }.mapTo(mutableSetOf()) { it.id }
                retryAt.keys.retainAll(pendingRetries)
                if (adaptConcurrency) queue.adaptConcurrency()
                while (mayStart() && inFlight.size < queue.maxConcurrent) {
                    val now = nowMillis()
                    val waiting = retryAt.filterValues { it > now }.keys
                    val next = queue.nextToStart(inFlight + waiting) ?: break
                    retryAt.remove(next.id)
                    inFlight.add(next.id)
                    queue.markRunning(next.id)
                    val running = queue.transfers.value.first { it.id == next.id }
                    launch {
                        try {
                            execute(running)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            // A paused/cancelled row belongs to the user; a worker error
                            // during teardown must not turn it into a new retry.
                            if (queue.transfers.value.any { it.id == running.id && it.state == TransferState.RUNNING }) {
                                queue.fail(running.id, TransferErrorKind.UNKNOWN)
                            }
                        } finally {
                            events.trySend(Event.Finished(running.id))
                        }
                    }
                }
                // Backoff expiry is an event in its own right. Without this timer a
                // queue with only retriable entries would never wake after its last failure.
                val nextDeadline = retryAt.values.filter { it > nowMillis() }.minOrNull()
                if (nextDeadline != null) {
                    retryTimer = launch {
                        delay((nextDeadline - nowMillis()).coerceAtLeast(1))
                        wake()
                    }
                }
            }
        } finally {
            retryTimer?.cancel()
            observer.cancel()
            limitObserver.cancel()
        }
    }

    fun wake() {
        // Progress samples can arrive faster than dispatch; retain one pending Wake
        // while preserving every Finished event (each frees an actual worker slot).
        if (wakePending.compareAndSet(false, true) && events.trySend(Event.Wake).isFailure) {
            wakePending.set(false)
        }
    }
    fun close() { job.cancel(); events.close() }
    suspend fun closeAndJoin() { close(); job.join() }

    private fun nowMillis(): Long = System.nanoTime() / 1_000_000
}
