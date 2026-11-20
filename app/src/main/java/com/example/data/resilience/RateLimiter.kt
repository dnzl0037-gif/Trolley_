package com.example.data.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

enum class RateLimitEndpoint(
    val key: String,
    val displayName: String,
    val maxTokens: Int,
    val windowSeconds: Long,
    val strictSensitive: Boolean,
) {
    AUTH_LOGIN(
        key = "auth_login",
        displayName = "Google Auth Sign-In",
        maxTokens = 5,
        windowSeconds = 60L,
        strictSensitive = true,
    ),
    AUTH_STEPUP_2FA(
        key = "auth_stepup_2fa",
        displayName = "2FA Step-Up & Re-Auth",
        maxTokens = 4,
        windowSeconds = 60L,
        strictSensitive = true,
    ),
    BILLING_CHECKOUT(
        key = "billing_checkout",
        displayName = "Payments & Subscriptions",
        maxTokens = 4,
        windowSeconds = 60L,
        strictSensitive = true,
    ),
    BOOKING_CREATE(
        key = "booking_create",
        displayName = "Booking Requests & Updates",
        maxTokens = 8,
        windowSeconds = 60L,
        strictSensitive = true,
    ),
    REVIEW_WRITE(
        key = "review_write",
        displayName = "Reviews & Ratings",
        maxTokens = 5,
        windowSeconds = 60L,
        strictSensitive = true,
    ),
    CHAT_SEND(
        key = "chat_send",
        displayName = "Pre-Booking Chat Messages",
        maxTokens = 20,
        windowSeconds = 30L,
        strictSensitive = false,
    ),
    DIRECTORY_READ(
        key = "directory_read",
        displayName = "Provider & Geocode Reads",
        maxTokens = 60,
        windowSeconds = 30L,
        strictSensitive = false,
    ),
}

sealed interface RateLimitDecision {
    data class Allowed(val remainingTokens: Int) : RateLimitDecision
    data class Rejected(
        val endpoint: RateLimitEndpoint,
        val retryAfterSeconds: Long,
    ) : RateLimitDecision
}

data class EndpointBucketStatus(
    val endpoint: RateLimitEndpoint,
    val availableTokens: Int,
    val maxTokens: Int,
    val rejectedCount: Long,
)

/**
 * Thread-safe sliding token-bucket rate limiter protecting public reads and
 * applying strict throttling on sensitive endpoints (auth, 2FA, payments, writes).
 */
class SlidingTokenBucketLimiter(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class BucketState(
        var tokens: Double,
        var lastRefillMs: Long,
        var rejectedCount: Long = 0L,
    )

    private val buckets = ConcurrentHashMap<String, BucketState>()
    private val lock = Any()

    private val _bucketStatuses = MutableStateFlow<List<EndpointBucketStatus>>(emptyList())
    val bucketStatuses: StateFlow<List<EndpointBucketStatus>> = _bucketStatuses.asStateFlow()

    init {
        publishStatuses()
    }

    fun tryAcquire(
        endpoint: RateLimitEndpoint,
        subjectKey: String = "device",
        cost: Double = 1.0,
    ): RateLimitDecision {
        val bucketKey = "${endpoint.key}:$subjectKey"
        val now = clockMs()
        val refillPerMs = endpoint.maxTokens.toDouble() / (endpoint.windowSeconds.toDouble() * 1000.0)

        val decision = synchronized(lock) {
            val state = buckets.getOrPut(bucketKey) {
                BucketState(
                    tokens = endpoint.maxTokens.toDouble(),
                    lastRefillMs = now,
                )
            }
            val elapsedMs = (now - state.lastRefillMs).coerceAtLeast(0L)
            if (elapsedMs > 0L) {
                state.tokens = (state.tokens + elapsedMs * refillPerMs).coerceAtMost(endpoint.maxTokens.toDouble())
                state.lastRefillMs = now
            }

            if (state.tokens >= cost) {
                state.tokens -= cost
                RateLimitDecision.Allowed(remainingTokens = state.tokens.toInt())
            } else {
                state.rejectedCount += 1L
                val missingTokens = (cost - state.tokens).coerceAtLeast(0.01)
                val waitMs = missingTokens / refillPerMs
                val retryAfterSeconds = ceil(waitMs / 1000.0).toLong().coerceAtLeast(1L)
                RateLimitDecision.Rejected(
                    endpoint = endpoint,
                    retryAfterSeconds = retryAfterSeconds,
                )
            }
        }
        publishStatuses()
        return decision
    }

    fun resetAll() {
        synchronized(lock) {
            buckets.clear()
        }
        publishStatuses()
    }

    private fun publishStatuses() {
        val now = clockMs()
        val list = RateLimitEndpoint.entries.map { endpoint ->
            val matching = buckets.entries.filter { it.key.startsWith("${endpoint.key}:") }
            val rejected = matching.sumOf { it.value.rejectedCount }
            val minTokens = if (matching.isEmpty()) {
                endpoint.maxTokens
            } else {
                matching.minOf { entry ->
                    val state = entry.value
                    val refillPerMs = endpoint.maxTokens.toDouble() / (endpoint.windowSeconds.toDouble() * 1000.0)
                    val elapsed = (now - state.lastRefillMs).coerceAtLeast(0L)
                    (state.tokens + elapsed * refillPerMs).coerceAtMost(endpoint.maxTokens.toDouble()).toInt()
                }
            }
            EndpointBucketStatus(
                endpoint = endpoint,
                availableTokens = minTokens,
                maxTokens = endpoint.maxTokens,
                rejectedCount = rejected,
            )
        }
        _bucketStatuses.value = list
    }
}
