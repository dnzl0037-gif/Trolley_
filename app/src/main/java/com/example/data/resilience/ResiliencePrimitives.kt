package com.example.data.resilience

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

sealed class TrolleyResilienceException(
    val httpStatus: Int,
    val retryAfterSeconds: Long? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class RateLimited(
        val endpoint: RateLimitEndpoint,
        retryAfterSeconds: Long,
    ) : TrolleyResilienceException(
        httpStatus = 429,
        retryAfterSeconds = retryAfterSeconds,
        message = "Too many ${endpoint.displayName.lowercase()} requests (HTTP 429). Retry after ${retryAfterSeconds}s.",
    )

    class CircuitOpen(
        val serviceName: String,
        retryAfterSeconds: Long,
    ) : TrolleyResilienceException(
        httpStatus = 503,
        retryAfterSeconds = retryAfterSeconds,
        message = "$serviceName is temporarily degraded (HTTP 503). Retry-After: ${retryAfterSeconds}s.",
    )

    class UpstreamTimeout(
        val operation: String,
        val timeoutMs: Long,
        cause: Throwable? = null,
    ) : TrolleyResilienceException(
        httpStatus = 504,
        retryAfterSeconds = 3L,
        message = "$operation timed out after ${timeoutMs / 1000}s. Please try again shortly.",
        cause = cause,
    )
}

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN,
}

data class CircuitBreakerStatus(
    val name: String,
    val state: CircuitState,
    val consecutiveFailures: Int,
    val tripCount: Long,
    val retryAfterSeconds: Long,
)

/**
 * Stateful resilience circuit breaker (CLOSED -> OPEN -> HALF_OPEN) protecting downstream calls.
 */
class CircuitBreaker(
    val name: String,
    private val failureThreshold: Int = 4,
    private val resetTimeoutMs: Long = 20_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var state: CircuitState = CircuitState.CLOSED
    private var consecutiveFailures: Int = 0
    private var openedAtMs: Long = 0L
    private var tripCount: Long = 0L
    private var halfOpenProbeInFlight: Boolean = false

    private val _status = MutableStateFlow(snapshotUnsafe())
    val status: StateFlow<CircuitBreakerStatus> = _status.asStateFlow()

    fun currentStatus(): CircuitBreakerStatus = synchronized(lock) { snapshotUnsafe() }

    fun allowRequest(): CircuitBreakerStatus? {
        val now = clockMs()
        val rejection: CircuitBreakerStatus? = synchronized(lock) {
            when (state) {
                CircuitState.CLOSED -> null
                CircuitState.OPEN -> {
                    val elapsed = now - openedAtMs
                    if (elapsed >= resetTimeoutMs) {
                        state = CircuitState.HALF_OPEN
                        halfOpenProbeInFlight = true
                        _status.value = snapshotUnsafe()
                        null
                    } else {
                        snapshotUnsafe()
                    }
                }
                CircuitState.HALF_OPEN -> {
                    if (halfOpenProbeInFlight) {
                        snapshotUnsafe()
                    } else {
                        halfOpenProbeInFlight = true
                        _status.value = snapshotUnsafe()
                        null
                    }
                }
            }
        }
        return rejection
    }

    fun recordSuccess() {
        synchronized(lock) {
            consecutiveFailures = 0
            state = CircuitState.CLOSED
            halfOpenProbeInFlight = false
            _status.value = snapshotUnsafe()
        }
    }

    fun recordFailure(countsTowardCircuit: Boolean = true) {
        if (!countsTowardCircuit) return
        val now = clockMs()
        synchronized(lock) {
            halfOpenProbeInFlight = false
            if (state == CircuitState.HALF_OPEN) {
                state = CircuitState.OPEN
                openedAtMs = now
                tripCount += 1L
                consecutiveFailures = failureThreshold
            } else {
                consecutiveFailures += 1
                if (consecutiveFailures >= failureThreshold) {
                    state = CircuitState.OPEN
                    openedAtMs = now
                    tripCount += 1L
                }
            }
            _status.value = snapshotUnsafe()
        }
    }

    fun reset() {
        synchronized(lock) {
            state = CircuitState.CLOSED
            consecutiveFailures = 0
            openedAtMs = 0L
            halfOpenProbeInFlight = false
            _status.value = snapshotUnsafe()
        }
    }

    private fun snapshotUnsafe(): CircuitBreakerStatus {
        val now = clockMs()
        val remainingSec = if (state == CircuitState.OPEN) {
            ((resetTimeoutMs - (now - openedAtMs)).coerceAtLeast(1000L) + 999L) / 1000L
        } else if (state == CircuitState.HALF_OPEN && halfOpenProbeInFlight) {
            2L
        } else {
            0L
        }
        return CircuitBreakerStatus(
            name = name,
            state = state,
            consecutiveFailures = consecutiveFailures,
            tripCount = tripCount,
            retryAfterSeconds = remainingSec,
        )
    }
}

object RetryPolicy {
    const val DEFAULT_BASE_DELAY_MS = 250L
    const val DEFAULT_MAX_DELAY_MS = 6_000L

    fun computeFullJitterDelayMs(
        attemptIndex: Int,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        randomUnit: Double = Random.nextDouble(),
    ): Long {
        val safeAttempt = attemptIndex.coerceIn(0, 10)
        val expCap = (baseDelayMs * 2.0.pow(safeAttempt)).toLong()
        val capped = min(maxDelayMs, expCap).coerceAtLeast(baseDelayMs)
        val clampedUnit = randomUnit.coerceIn(0.0, 1.0)
        return (capped * clampedUnit).toLong().coerceAtLeast(10L)
    }

    fun isTransientError(error: Throwable): Boolean {
        val root = generateSequence(error) { it.cause }.firstOrNull {
            it is FirebaseFirestoreException ||
                it is FirebaseNetworkException ||
                it is TimeoutCancellationException ||
                it is TrolleyResilienceException.UpstreamTimeout ||
                it is IOException
        } ?: error

        return when (root) {
            is TimeoutCancellationException -> true
            is TrolleyResilienceException.UpstreamTimeout -> true
            is TrolleyResilienceException.RateLimited -> false
            is TrolleyResilienceException.CircuitOpen -> false
            is FirebaseNetworkException -> true
            is IOException -> true
            is FirebaseFirestoreException -> root.code in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.ABORTED,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                FirebaseFirestoreException.Code.INTERNAL,
            )
            else -> false
        }
    }

    fun countsAsDownstreamFailure(error: Throwable): Boolean {
        if (error is TrolleyResilienceException.RateLimited) return false
        if (error is IllegalArgumentException || error is IllegalStateException) return false
        val firestoreEx = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreEx != null) {
            return firestoreEx.code in setOf(
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                FirebaseFirestoreException.Code.INTERNAL,
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                FirebaseFirestoreException.Code.UNKNOWN,
            )
        }
        return isTransientError(error)
    }

    suspend fun <T> executeResilient(
        operationName: String,
        timeoutMs: Long,
        maxAttempts: Int = 3,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        circuitBreaker: CircuitBreaker? = null,
        isRetryable: (Throwable) -> Boolean = ::isTransientError,
        onRetry: () -> Unit = {},
        block: suspend () -> T,
    ): T {
        if (circuitBreaker != null) {
            val rejection = circuitBreaker.allowRequest()
            if (rejection != null) {
                throw TrolleyResilienceException.CircuitOpen(
                    serviceName = circuitBreaker.name,
                    retryAfterSeconds = rejection.retryAfterSeconds.coerceAtLeast(1L),
                )
            }
        }

        var lastError: Throwable? = null
        val safeAttempts = maxAttempts.coerceAtLeast(1)

        for (attempt in 0 until safeAttempts) {
            try {
                val value = withTimeout(timeoutMs) {
                    block()
                }
                circuitBreaker?.recordSuccess()
                return value
            } catch (cancel: CancellationException) {
                if (cancel is TimeoutCancellationException) {
                    val wrapped = TrolleyResilienceException.UpstreamTimeout(
                        operation = operationName,
                        timeoutMs = timeoutMs,
                        cause = cancel,
                    )
                    lastError = wrapped
                    val shouldRetry = attempt < safeAttempts - 1 && isRetryable(wrapped)
                    if (!shouldRetry) {
                        circuitBreaker?.recordFailure(countsTowardCircuit = true)
                        throw wrapped
                    }
                    onRetry()
                    delay(computeFullJitterDelayMs(attempt, baseDelayMs, maxDelayMs))
                } else {
                    throw cancel
                }
            } catch (error: Throwable) {
                lastError = error
                val canRetry = attempt < safeAttempts - 1 && isRetryable(error)
                if (!canRetry) {
                    circuitBreaker?.recordFailure(countsTowardCircuit = countsAsDownstreamFailure(error))
                    throw error
                }
                onRetry()
                delay(computeFullJitterDelayMs(attempt, baseDelayMs, maxDelayMs))
            }
        }

        val terminal = lastError ?: IllegalStateException("$operationName failed without exception detail.")
        circuitBreaker?.recordFailure(countsTowardCircuit = countsAsDownstreamFailure(terminal))
        throw terminal
    }
}

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
}

data class HealthCheckReport(
    val status: HealthStatus = HealthStatus.HEALTHY,
    val checkedAtMs: Long = 0L,
    val authStatus: String = "Ready",
    val firestoreReachable: Boolean = true,
    val firestoreLatencyMs: Long = 0L,
    val cacheStatus: String = "Active",
    val workerPoolStatus: String = "Idle",
    val summary: String = "All core dependencies responding within timeout budget.",
)
