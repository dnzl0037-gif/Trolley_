package com.example.data.resilience

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.resilienceQueueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "trolley_resilience_queue"
)

enum class BackgroundJobType(val label: String) {
    RECEIPT_DISPATCH("Billing Receipt Dispatch"),
    NOTIFICATION_SYNC("Booking / Chat Alert Sync"),
    MEDIA_PREP("Avatar / Image Normalization"),
    TELEMETRY_FLUSH("Observability Audit Flush"),
}

@Serializable
data class QueuedJob(
    val id: String,
    val type: String,
    val idempotencyKey: String,
    val payloadSummary: String,
    val attempt: Int = 0,
    val maxAttempts: Int = 4,
    val enqueuedAtMs: Long = System.currentTimeMillis(),
    val nextRunAtMs: Long = System.currentTimeMillis(),
    val forceFailForTest: Boolean = false,
)

@Serializable
data class DeadLetterJob(
    val id: String,
    val type: String,
    val idempotencyKey: String,
    val payloadSummary: String,
    val attemptsMade: Int,
    val lastError: String,
    val failedAtMs: Long,
)

data class QueueStatusSnapshot(
    val workerCount: Int = 2,
    val activeWorkers: Int = 0,
    val queueDepth: Int = 0,
    val deadLetterDepth: Int = 0,
    val processedCount: Long = 0L,
    val deduplicatedCount: Long = 0L,
    val recommendedWorkers: Int = 2,
    val pendingJobs: List<QueuedJob> = emptyList(),
    val deadLetterJobs: List<DeadLetterJob> = emptyList(),
)

/**
 * Persistent background job queue with configurable worker concurrency, idempotency
 * deduplication, exponential backoff + jitter retries, and a Dead-Letter Queue (DLQ).
 */
class BackgroundJobQueue(
    context: Context? = null,
    private val metrics: ResilienceMetricsRegistry? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val customExecutor: (suspend (QueuedJob) -> Result<Unit>)? = null,
) {
    private val dataStore: DataStore<Preferences>? = context?.applicationContext?.resilienceQueueDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val pendingQueue = mutableListOf<QueuedJob>()
    private val dlqList = mutableListOf<DeadLetterJob>()
    private val processedIdempotencyKeys = LinkedHashSet<String>()

    private var workerConcurrency: Int = DEFAULT_WORKERS
    private var activeWorkersCount: Int = 0
    private var processedTotal: Long = 0L
    private var deduplicatedTotal: Long = 0L

    private val _status = MutableStateFlow(QueueStatusSnapshot())
    val status: StateFlow<QueueStatusSnapshot> = _status.asStateFlow()

    init {
        if (dataStore != null) {
            scope.launch {
                restoreFromDisk()
                scheduleWorkerDispatch()
            }
        }
    }

    suspend fun enqueue(
        type: BackgroundJobType,
        idempotencyKey: String,
        payloadSummary: String,
        maxAttempts: Int = 4,
        forceFailForTest: Boolean = false,
    ): Boolean {
        val cleanKey = idempotencyKey.trim()
        require(cleanKey.isNotEmpty()) { "Idempotency key is required for background jobs." }

        val accepted = mutex.withLock {
            if (cleanKey in processedIdempotencyKeys ||
                pendingQueue.any { it.idempotencyKey == cleanKey } ||
                dlqList.any { it.idempotencyKey == cleanKey }
            ) {
                deduplicatedTotal += 1L
                StructuredLogger.log(
                    traceId = cleanKey.take(12),
                    component = "JobQueue",
                    operation = "enqueue_${type.name}",
                    status = "DEDUPLICATED",
                    httpEquiv = 200,
                    latencyMs = 0L,
                    detail = "Skipped duplicate idempotencyKey=$cleanKey",
                )
                false
            } else {
                val now = System.currentTimeMillis()
                pendingQueue += QueuedJob(
                    id = "job_${UUID.randomUUID().toString().take(8)}",
                    type = type.name,
                    idempotencyKey = cleanKey,
                    payloadSummary = payloadSummary.take(160),
                    attempt = 0,
                    maxAttempts = maxAttempts.coerceIn(1, 8),
                    enqueuedAtMs = now,
                    nextRunAtMs = now,
                    forceFailForTest = forceFailForTest,
                )
                true
            }
        }

        persistAndPublish()
        if (accepted) {
            scheduleWorkerDispatch()
        }
        return accepted
    }

    suspend fun setWorkerCount(targetWorkers: Int) {
        mutex.withLock {
            workerConcurrency = targetWorkers.coerceIn(MIN_WORKERS, MAX_WORKERS)
        }
        persistAndPublish()
        scheduleWorkerDispatch()
    }

    suspend fun replayDeadLetterJob(jobId: String): Boolean {
        val moved = mutex.withLock {
            val idx = dlqList.indexOfFirst { it.id == jobId }
            if (idx < 0) return@withLock false
            val dlq = dlqList.removeAt(idx)
            val now = System.currentTimeMillis()
            pendingQueue += QueuedJob(
                id = dlq.id,
                type = dlq.type,
                idempotencyKey = dlq.idempotencyKey,
                payloadSummary = dlq.payloadSummary,
                attempt = 0,
                maxAttempts = 4,
                enqueuedAtMs = now,
                nextRunAtMs = now,
                forceFailForTest = false,
            )
            true
        }
        persistAndPublish()
        if (moved) scheduleWorkerDispatch()
        return moved
    }

    suspend fun clearDeadLetterQueue() {
        mutex.withLock {
            dlqList.clear()
        }
        persistAndPublish()
    }

    /**
     * Deterministic drain helper primarily used in tests or manual admin triggers.
     */
    suspend fun processNextAvailableNow(): Boolean {
        val job = mutex.withLock {
            val now = System.currentTimeMillis()
            val idx = pendingQueue.indexOfFirst { it.nextRunAtMs <= now }
            if (idx < 0) return@withLock null
            activeWorkersCount += 1
            pendingQueue.removeAt(idx)
        } ?: return false

        publishOnly()
        try {
            val started = System.currentTimeMillis()
            val result = executeJob(job)
            val elapsed = System.currentTimeMillis() - started

            mutex.withLock {
                activeWorkersCount = (activeWorkersCount - 1).coerceAtLeast(0)
                result.fold(
                    onSuccess = {
                        processedIdempotencyKeys.add(job.idempotencyKey)
                        while (processedIdempotencyKeys.size > MAX_IDEMPOTENCY_KEYS) {
                            val first = processedIdempotencyKeys.firstOrNull() ?: break
                            processedIdempotencyKeys.remove(first)
                        }
                        processedTotal += 1L
                        StructuredLogger.log(
                            traceId = job.id,
                            component = "WorkerPool",
                            operation = job.type,
                            status = "COMPLETED",
                            httpEquiv = 200,
                            latencyMs = elapsed,
                            detail = job.payloadSummary,
                        )
                    },
                    onFailure = { error ->
                        val nextAttempt = job.attempt + 1
                        if (nextAttempt >= job.maxAttempts) {
                            dlqList += DeadLetterJob(
                                id = job.id,
                                type = job.type,
                                idempotencyKey = job.idempotencyKey,
                                payloadSummary = job.payloadSummary,
                                attemptsMade = nextAttempt,
                                lastError = error.message ?: "Unknown worker error",
                                failedAtMs = System.currentTimeMillis(),
                            )
                            StructuredLogger.log(
                                traceId = job.id,
                                component = "WorkerPool",
                                operation = job.type,
                                status = "MOVED_TO_DLQ",
                                httpEquiv = 500,
                                latencyMs = elapsed,
                                severity = LogSeverity.ERROR,
                                detail = "Exhausted ${job.maxAttempts} attempts: ${error.message}",
                            )
                        } else {
                            val backoff = RetryPolicy.computeFullJitterDelayMs(nextAttempt)
                            pendingQueue += job.copy(
                                attempt = nextAttempt,
                                nextRunAtMs = System.currentTimeMillis() + backoff,
                            )
                            metrics?.recordRetry()
                            StructuredLogger.log(
                                traceId = job.id,
                                component = "WorkerPool",
                                operation = job.type,
                                status = "RETRY_SCHEDULED",
                                httpEquiv = 503,
                                latencyMs = elapsed,
                                severity = LogSeverity.WARN,
                                detail = "Attempt $nextAttempt/${job.maxAttempts}, retry in ${backoff}ms",
                            )
                        }
                    },
                )
            }
            persistAndPublish()
        } catch (t: Throwable) {
            mutex.withLock {
                activeWorkersCount = (activeWorkersCount - 1).coerceAtLeast(0)
            }
            publishOnly()
            throw t
        }
        return true
    }

    private fun scheduleWorkerDispatch() {
        scope.launch {
            while (true) {
                val canRunMore = mutex.withLock {
                    activeWorkersCount < workerConcurrency && pendingQueue.isNotEmpty()
                }
                if (!canRunMore) break

                val nextWaitMs = mutex.withLock {
                    val now = System.currentTimeMillis()
                    val ready = pendingQueue.any { it.nextRunAtMs <= now }
                    if (ready) 0L else {
                        val earliest = pendingQueue.minOfOrNull { it.nextRunAtMs } ?: now
                        (earliest - now).coerceIn(25L, 2_000L)
                    }
                }
                if (nextWaitMs > 0L) {
                    delay(nextWaitMs)
                }
                val didWork = processNextAvailableNow()
                if (!didWork) break
            }
        }
    }

    private suspend fun executeJob(job: QueuedJob): Result<Unit> {
        if (customExecutor != null) {
            return customExecutor.invoke(job)
        }
        if (job.forceFailForTest) {
            return Result.failure(IllegalStateException("Simulated downstream failure for DLQ resilience test."))
        }
        // Lightweight async background execution simulation (never hits arbitrary unverified network hosts)
        delay(35L)
        return Result.success(Unit)
    }

    private suspend fun restoreFromDisk() {
        val ds = dataStore ?: return
        val prefs = runCatching { ds.data.first() }.getOrNull() ?: return
        mutex.withLock {
            workerConcurrency = (prefs[KEY_WORKERS] ?: DEFAULT_WORKERS).coerceIn(MIN_WORKERS, MAX_WORKERS)
            prefs[KEY_PENDING]?.let { raw ->
                runCatching { json.decodeFromString<List<QueuedJob>>(raw) }.getOrNull()?.let {
                    pendingQueue.clear()
                    pendingQueue.addAll(it)
                }
            }
            prefs[KEY_DLQ]?.let { raw ->
                runCatching { json.decodeFromString<List<DeadLetterJob>>(raw) }.getOrNull()?.let {
                    dlqList.clear()
                    dlqList.addAll(it)
                }
            }
            prefs[KEY_IDEMPOTENCY]?.let { raw ->
                runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()?.let {
                    processedIdempotencyKeys.clear()
                    processedIdempotencyKeys.addAll(it)
                }
            }
        }
        publishOnly()
    }

    private suspend fun persistAndPublish() {
        val snapshot = publishOnly()
        val ds = dataStore ?: return
        val keysSnapshot = mutex.withLock { processedIdempotencyKeys.toList() }
        runCatching {
            ds.edit { prefs ->
                prefs[KEY_WORKERS] = snapshot.workerCount
                prefs[KEY_PENDING] = json.encodeToString(snapshot.pendingJobs)
                prefs[KEY_DLQ] = json.encodeToString(snapshot.deadLetterJobs)
                prefs[KEY_IDEMPOTENCY] = json.encodeToString(keysSnapshot)
            }
        }
    }

    private suspend fun publishOnly(): QueueStatusSnapshot {
        val snapshot = mutex.withLock {
            val depth = pendingQueue.size
            val recommended = when {
                depth >= 12 -> MAX_WORKERS
                depth >= 6 -> (MAX_WORKERS / 2).coerceAtLeast(DEFAULT_WORKERS + 1)
                else -> workerConcurrency
            }
            QueueStatusSnapshot(
                workerCount = workerConcurrency,
                activeWorkers = activeWorkersCount,
                queueDepth = depth,
                deadLetterDepth = dlqList.size,
                processedCount = processedTotal,
                deduplicatedCount = deduplicatedTotal,
                recommendedWorkers = recommended,
                pendingJobs = pendingQueue.toList(),
                deadLetterJobs = dlqList.toList(),
            )
        }
        _status.value = snapshot
        metrics?.updateQueueStats(
            depth = snapshot.queueDepth,
            dlqDepth = snapshot.deadLetterDepth,
            workers = snapshot.workerCount,
        )
        return snapshot
    }

    companion object {
        const val MIN_WORKERS = 1
        const val MAX_WORKERS = 8
        const val DEFAULT_WORKERS = 2
        private const val MAX_IDEMPOTENCY_KEYS = 250

        private val KEY_WORKERS = intPreferencesKey("worker_concurrency")
        private val KEY_PENDING = stringPreferencesKey("pending_jobs_json")
        private val KEY_DLQ = stringPreferencesKey("dlq_jobs_json")
        private val KEY_IDEMPOTENCY = stringPreferencesKey("idempotency_keys_json")
    }
}
