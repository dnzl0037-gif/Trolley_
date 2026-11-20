package com.example.data.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

enum class LogSeverity {
    INFO,
    WARN,
    ERROR,
}

data class StructuredLogEvent(
    val timestampMs: Long,
    val traceId: String,
    val component: String,
    val operation: String,
    val status: String,
    val httpEquiv: Int,
    val latencyMs: Long,
    val severity: LogSeverity,
    val detail: String = "",
)

enum class AlertSeverity {
    OK,
    WARNING,
    CRITICAL,
}

data class ResilienceAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: AlertSeverity,
)

data class MetricsSnapshot(
    val totalRequests: Long = 0L,
    val failedRequests: Long = 0L,
    val rateLimited429Count: Long = 0L,
    val circuitRejected503Count: Long = 0L,
    val timeout504Count: Long = 0L,
    val retryCount: Long = 0L,
    val cacheHits: Long = 0L,
    val cacheMisses: Long = 0L,
    val coalescedRequests: Long = 0L,
    val activeListeners: Int = 0,
    val avgLatencyMs: Long = 0L,
    val p95LatencyMs: Long = 0L,
    val errorRatePercent: Double = 0.0,
    val cacheHitRatioPercent: Double = 0.0,
    val queueDepth: Int = 0,
    val deadLetterDepth: Int = 0,
    val configuredWorkers: Int = 2,
    val perOperationCounts: Map<String, Long> = emptyMap(),
)

object StructuredLogger {
    private const val MAX_EVENTS = 120
    private val _events = MutableStateFlow<List<StructuredLogEvent>>(emptyList())
    val events: StateFlow<List<StructuredLogEvent>> = _events.asStateFlow()

    private val lock = Any()
    private val ringBuffer = ArrayDeque<StructuredLogEvent>(MAX_EVENTS)

    fun log(
        traceId: String,
        component: String,
        operation: String,
        status: String,
        httpEquiv: Int,
        latencyMs: Long,
        severity: LogSeverity = LogSeverity.INFO,
        detail: String = "",
        emitSystemLog: Boolean = true,
    ) {
        val event = StructuredLogEvent(
            timestampMs = System.currentTimeMillis(),
            traceId = traceId,
            component = component,
            operation = operation,
            status = status,
            httpEquiv = httpEquiv,
            latencyMs = latencyMs,
            severity = severity,
            detail = detail.take(240),
        )
        synchronized(lock) {
            if (ringBuffer.size >= MAX_EVENTS) {
                ringBuffer.removeLast()
            }
            ringBuffer.addFirst(event)
            _events.value = ringBuffer.toList()
        }
        if (emitSystemLog) {
            val line =
                "ts=${event.timestampMs} trace=${event.traceId} component=${event.component} " +
                    "op=${event.operation} status=${event.status} http=${event.httpEquiv} " +
                    "latencyMs=${event.latencyMs} detail=${event.detail}"
            runCatching {
                when (severity) {
                    LogSeverity.INFO -> android.util.Log.i("TrolleyObs", line)
                    LogSeverity.WARN -> android.util.Log.w("TrolleyObs", line)
                    LogSeverity.ERROR -> android.util.Log.e("TrolleyObs", line)
                }
            }
        }
    }

    fun clearForTests() {
        synchronized(lock) {
            ringBuffer.clear()
            _events.value = emptyList()
        }
    }
}

class ResilienceMetricsRegistry {
    private val totalRequests = AtomicLong(0L)
    private val failedRequests = AtomicLong(0L)
    private val rateLimited429Count = AtomicLong(0L)
    private val circuitRejected503Count = AtomicLong(0L)
    private val timeout504Count = AtomicLong(0L)
    private val retryCount = AtomicLong(0L)
    private val cacheHits = AtomicLong(0L)
    private val cacheMisses = AtomicLong(0L)
    private val coalescedRequests = AtomicLong(0L)
    private val activeListeners = AtomicInteger(0)

    private val queueDepth = AtomicInteger(0)
    private val deadLetterDepth = AtomicInteger(0)
    private val configuredWorkers = AtomicInteger(2)

    private val opCounts = ConcurrentHashMap<String, AtomicLong>()
    private val latencyLock = Any()
    private val latencySamples = ArrayDeque<Long>(MAX_LATENCY_SAMPLES)

    private val _snapshot = MutableStateFlow(MetricsSnapshot())
    val snapshot: StateFlow<MetricsSnapshot> = _snapshot.asStateFlow()

    fun recordRequest(
        operation: String,
        latencyMs: Long,
        httpStatus: Int,
        success: Boolean,
    ) {
        totalRequests.incrementAndGet()
        if (!success) {
            failedRequests.incrementAndGet()
        }
        when (httpStatus) {
            429 -> rateLimited429Count.incrementAndGet()
            503 -> circuitRejected503Count.incrementAndGet()
            504 -> timeout504Count.incrementAndGet()
        }
        opCounts.computeIfAbsent(operation) { AtomicLong(0L) }.incrementAndGet()

        synchronized(latencyLock) {
            if (latencySamples.size >= MAX_LATENCY_SAMPLES) {
                latencySamples.removeFirst()
            }
            latencySamples.addLast(latencyMs.coerceAtLeast(0L))
        }
        publish()
    }

    fun recordRetry() {
        retryCount.incrementAndGet()
        publish()
    }

    fun recordCacheHit() {
        cacheHits.incrementAndGet()
        publish()
    }

    fun recordCacheMiss() {
        cacheMisses.incrementAndGet()
        publish()
    }

    fun recordCoalesced() {
        coalescedRequests.incrementAndGet()
        publish()
    }

    fun onListenerStarted() {
        activeListeners.incrementAndGet()
        publish()
    }

    fun onListenerStopped() {
        activeListeners.updateAndGet { (it - 1).coerceAtLeast(0) }
        publish()
    }

    fun updateQueueStats(depth: Int, dlqDepth: Int, workers: Int) {
        queueDepth.set(depth.coerceAtLeast(0))
        deadLetterDepth.set(dlqDepth.coerceAtLeast(0))
        configuredWorkers.set(workers.coerceAtLeast(1))
        publish()
    }

    fun evaluateAlerts(openCircuits: List<String>): List<ResilienceAlert> {
        val current = _snapshot.value
        val alerts = mutableListOf<ResilienceAlert>()

        if (openCircuits.isNotEmpty()) {
            alerts += ResilienceAlert(
                id = "circuit_open",
                title = "Circuit Breaker Tripped",
                message = "Open breaker(s): ${openCircuits.joinToString(", ")}. Failing fast with 503 Retry-After.",
                severity = AlertSeverity.CRITICAL,
            )
        }
        if (current.deadLetterDepth > 0) {
            alerts += ResilienceAlert(
                id = "dlq_non_empty",
                title = "Dead-Letter Queue Attention",
                message = "${current.deadLetterDepth} job(s) exhausted retries and await replay or inspection.",
                severity = AlertSeverity.WARNING,
            )
        }
        if (current.queueDepth >= 12) {
            alerts += ResilienceAlert(
                id = "queue_backlog",
                title = "High Queue Depth",
                message = "${current.queueDepth} queued jobs. Recommended scaling: add workers.",
                severity = AlertSeverity.WARNING,
            )
        }
        if (current.totalRequests >= 10 && current.errorRatePercent >= 15.0) {
            alerts += ResilienceAlert(
                id = "high_error_rate",
                title = "Elevated Error Rate",
                message = String.format(java.util.Locale.US, "Rolling error rate at %.1f%%.", current.errorRatePercent),
                severity = AlertSeverity.CRITICAL,
            )
        }
        if (current.rateLimited429Count >= 5) {
            alerts += ResilienceAlert(
                id = "rate_limit_spikes",
                title = "Traffic Spike Protection Active",
                message = "${current.rateLimited429Count} request burst(s) throttled gracefully with HTTP 429.",
                severity = AlertSeverity.WARNING,
            )
        }
        if (alerts.isEmpty()) {
            alerts += ResilienceAlert(
                id = "all_nominal",
                title = "All Resilience Systems Nominal",
                message = "Latencies, error budget, cache ratio, and worker pools are healthy.",
                severity = AlertSeverity.OK,
            )
        }
        return alerts
    }

    private fun publish() {
        val samples = synchronized(latencyLock) { latencySamples.sorted() }
        val avg = if (samples.isEmpty()) 0L else (samples.sum() / samples.size)
        val p95 = if (samples.isEmpty()) 0L else {
            val idx = ceil(samples.size * 0.95).toInt().coerceIn(1, samples.size) - 1
            samples[idx]
        }
        val total = totalRequests.get()
        val failed = failedRequests.get()
        val errorPct = if (total == 0L) 0.0 else (failed.toDouble() * 100.0 / total.toDouble())
        val hits = cacheHits.get()
        val misses = cacheMisses.get()
        val cacheTotal = hits + misses
        val hitPct = if (cacheTotal == 0L) 0.0 else (hits.toDouble() * 100.0 / cacheTotal.toDouble())

        _snapshot.value = MetricsSnapshot(
            totalRequests = total,
            failedRequests = failed,
            rateLimited429Count = rateLimited429Count.get(),
            circuitRejected503Count = circuitRejected503Count.get(),
            timeout504Count = timeout504Count.get(),
            retryCount = retryCount.get(),
            cacheHits = hits,
            cacheMisses = misses,
            coalescedRequests = coalescedRequests.get(),
            activeListeners = activeListeners.get(),
            avgLatencyMs = avg,
            p95LatencyMs = p95,
            errorRatePercent = errorPct,
            cacheHitRatioPercent = hitPct,
            queueDepth = queueDepth.get(),
            deadLetterDepth = deadLetterDepth.get(),
            configuredWorkers = configuredWorkers.get(),
            perOperationCounts = opCounts.mapValues { it.value.get() },
        )
    }

    private companion object {
        const val MAX_LATENCY_SAMPLES = 200
    }
}
