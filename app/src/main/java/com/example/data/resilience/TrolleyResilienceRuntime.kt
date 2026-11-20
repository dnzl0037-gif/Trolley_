package com.example.data.resilience

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Centralized resilience coordinator sharing rate limiters, circuit breakers,
 * TTL response cache, single-flight coalescer, background worker queue,
 * and structured observability metrics across the entire app lifecycle.
 */
class TrolleyResilienceRuntime(
    context: Context? = null,
) {
    val metrics = ResilienceMetricsRegistry()
    val rateLimiter = SlidingTokenBucketLimiter()
    val readCache = TtlResponseCache<String, Any>(
        maxEntries = 160,
        defaultTtlMs = 60_000L,
        onHit = { metrics.recordCacheHit() },
        onMiss = { metrics.recordCacheMiss() },
    )
    val singleFlight = SingleFlightCoordinator(
        onCoalesced = { metrics.recordCoalesced() },
    )
    val databaseRouter = DatabaseAccessRouter(
        cache = readCache,
        singleFlight = singleFlight,
    )

    val firestoreReadBreaker = CircuitBreaker(name = "firestore_read", failureThreshold = 5, resetTimeoutMs = 20_000L)
    val firestoreWriteBreaker = CircuitBreaker(name = "firestore_write", failureThreshold = 4, resetTimeoutMs = 20_000L)
    val authBreaker = CircuitBreaker(name = "firebase_auth", failureThreshold = 4, resetTimeoutMs = 25_000L)
    val billingBreaker = CircuitBreaker(name = "billing_gateway", failureThreshold = 3, resetTimeoutMs = 30_000L)
    val geocoderBreaker = CircuitBreaker(name = "address_geocoder", failureThreshold = 5, resetTimeoutMs = 30_000L)

    val allBreakers: List<CircuitBreaker> = listOf(
        firestoreReadBreaker,
        firestoreWriteBreaker,
        authBreaker,
        billingBreaker,
        geocoderBreaker,
    )

    val jobQueue = BackgroundJobQueue(
        context = context,
        metrics = metrics,
    )

    private val _healthReport = MutableStateFlow(HealthCheckReport())
    val healthReport: StateFlow<HealthCheckReport> = _healthReport.asStateFlow()

    fun acquireRateLimitOrThrow(
        endpoint: RateLimitEndpoint,
        subjectKey: String = "device",
    ) {
        when (val decision = rateLimiter.tryAcquire(endpoint, subjectKey)) {
            is RateLimitDecision.Allowed -> Unit
            is RateLimitDecision.Rejected -> {
                val traceId = UUID.randomUUID().toString().take(8)
                metrics.recordRequest(
                    operation = endpoint.key,
                    latencyMs = 0L,
                    httpStatus = 429,
                    success = false,
                )
                StructuredLogger.log(
                    traceId = traceId,
                    component = "RateLimiter",
                    operation = endpoint.key,
                    status = "RATE_LIMITED",
                    httpEquiv = 429,
                    latencyMs = 0L,
                    severity = LogSeverity.WARN,
                    detail = "subject=$subjectKey retryAfter=${decision.retryAfterSeconds}s",
                )
                throw TrolleyResilienceException.RateLimited(
                    endpoint = decision.endpoint,
                    retryAfterSeconds = decision.retryAfterSeconds,
                )
            }
        }
    }

    suspend fun <T> executeGuarded(
        operationName: String,
        endpoint: RateLimitEndpoint? = null,
        subjectKey: String = "device",
        timeoutMs: Long = FIRESTORE_WRITE_TIMEOUT_MS,
        maxAttempts: Int = 2,
        circuitBreaker: CircuitBreaker? = firestoreWriteBreaker,
        block: suspend () -> T,
    ): T {
        val traceId = UUID.randomUUID().toString().take(8)
        if (endpoint != null) {
            acquireRateLimitOrThrow(endpoint, subjectKey)
        }
        val started = System.currentTimeMillis()
        return try {
            val result = RetryPolicy.executeResilient(
                operationName = operationName,
                timeoutMs = timeoutMs,
                maxAttempts = maxAttempts,
                circuitBreaker = circuitBreaker,
                onRetry = { metrics.recordRetry() },
                block = block,
            )
            val elapsed = System.currentTimeMillis() - started
            metrics.recordRequest(
                operation = operationName,
                latencyMs = elapsed,
                httpStatus = 200,
                success = true,
            )
            StructuredLogger.log(
                traceId = traceId,
                component = "Repository",
                operation = operationName,
                status = "OK",
                httpEquiv = 200,
                latencyMs = elapsed,
            )
            result
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - started
            val httpEquiv = when (t) {
                is TrolleyResilienceException -> t.httpStatus
                is IllegalArgumentException -> 400
                is IllegalStateException -> 401
                else -> 500
            }
            metrics.recordRequest(
                operation = operationName,
                latencyMs = elapsed,
                httpStatus = httpEquiv,
                success = false,
            )
            StructuredLogger.log(
                traceId = traceId,
                component = "Repository",
                operation = operationName,
                status = "FAILED",
                httpEquiv = httpEquiv,
                latencyMs = elapsed,
                severity = if (httpEquiv >= 500) LogSeverity.ERROR else LogSeverity.WARN,
                detail = t.message ?: t.javaClass.simpleName,
            )
            throw t
        }
    }

    suspend fun runHealthCheck(
        db: FirebaseFirestore,
        auth: FirebaseAuth,
    ): HealthCheckReport {
        val started = System.currentTimeMillis()
        val user = auth.currentUser
        val authStatus = if (user != null) "Authenticated (${user.uid.take(6)}…)" else "Ready (Anonymous / Signed out)"

        var firestoreOk = true
        var latencyMs = 0L
        var detail = "All systems responding normally."

        if (user != null) {
            val probeStart = System.currentTimeMillis()
            val probeResult = runCatching {
                withTimeout(4_000L) {
                    db.collection("users").document(user.uid).get(Source.DEFAULT).await()
                }
            }
            latencyMs = (System.currentTimeMillis() - probeStart).coerceAtLeast(1L)
            if (probeResult.isFailure) {
                firestoreOk = false
                detail = probeResult.exceptionOrNull()?.localizedMessage ?: "Firestore probe failed"
            }
        } else {
            latencyMs = (System.currentTimeMillis() - started).coerceAtLeast(1L)
        }

        val openBreakers = allBreakers.filter { it.currentStatus().state == CircuitState.OPEN }
        val qStatus = jobQueue.status.value
        val overallStatus = when {
            !firestoreOk && openBreakers.isNotEmpty() -> HealthStatus.UNAVAILABLE
            !firestoreOk || openBreakers.isNotEmpty() || qStatus.deadLetterDepth > 0 -> HealthStatus.DEGRADED
            else -> HealthStatus.HEALTHY
        }

        val report = HealthCheckReport(
            status = overallStatus,
            checkedAtMs = System.currentTimeMillis(),
            authStatus = authStatus,
            firestoreReachable = firestoreOk,
            firestoreLatencyMs = latencyMs,
            cacheStatus = "${readCache.stats.value.cachedEntries} cached entries",
            workerPoolStatus = "${qStatus.workerCount} worker(s) · depth=${qStatus.queueDepth} · dlq=${qStatus.deadLetterDepth}",
            summary = if (openBreakers.isNotEmpty()) {
                "Open circuit(s): ${openBreakers.joinToString { it.name }}"
            } else {
                detail
            },
        )
        _healthReport.value = report
        StructuredLogger.log(
            traceId = "healthz",
            component = "HealthCheck",
            operation = "probe",
            status = overallStatus.name,
            httpEquiv = if (overallStatus == HealthStatus.UNAVAILABLE) 503 else 200,
            latencyMs = latencyMs,
            severity = if (overallStatus == HealthStatus.HEALTHY) LogSeverity.INFO else LogSeverity.WARN,
            detail = report.summary,
        )
        return report
    }

    fun openCircuitNames(): List<String> =
        allBreakers.filter { it.currentStatus().state == CircuitState.OPEN }.map { it.name }

    fun resetAllCircuits() {
        allBreakers.forEach { it.reset() }
    }

    companion object {
        const val FIRESTORE_READ_TIMEOUT_MS = 8_000L
        const val FIRESTORE_WRITE_TIMEOUT_MS = 10_000L
        const val CHECKOUT_TIMEOUT_MS = 12_000L

        @Volatile
        private var instance: TrolleyResilienceRuntime? = null

        fun shared(context: Context? = null): TrolleyResilienceRuntime {
            val existing = instance
            if (existing != null) return existing
            return synchronized(this) {
                instance ?: TrolleyResilienceRuntime(context?.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
