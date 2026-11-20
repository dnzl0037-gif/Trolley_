package com.example.data.resilience

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CacheEntryMetadata(
    val cachedEntries: Int = 0,
    val maxEntries: Int = 128,
    val defaultTtlMs: Long = 60_000L,
)

/**
 * Bounded thread-safe LRU + TTL in-memory cache layer that absorbs hot-read spikes
 * before hitting Firestore, matching Redis TTL semantics cleanly inside the app process.
 */
class TtlResponseCache<K : Any, V : Any>(
    private val maxEntries: Int = 128,
    private val defaultTtlMs: Long = 60_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val onHit: () -> Unit = {},
    private val onMiss: () -> Unit = {},
) {
    private data class CachedItem<V>(
        val value: V,
        val expiresAtMs: Long,
    )

    private val lock = Any()
    private val store = object : LinkedHashMap<K, CachedItem<V>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CachedItem<V>>?): Boolean {
            return size > maxEntries
        }
    }

    private val _stats = MutableStateFlow(
        CacheEntryMetadata(cachedEntries = 0, maxEntries = maxEntries, defaultTtlMs = defaultTtlMs)
    )
    val stats: StateFlow<CacheEntryMetadata> = _stats.asStateFlow()

    fun get(key: K): V? {
        val now = clockMs()
        val found = synchronized(lock) {
            val item = store[key]
            if (item == null) {
                null
            } else if (now >= item.expiresAtMs) {
                store.remove(key)
                _stats.value = _stats.value.copy(cachedEntries = store.size)
                null
            } else {
                item.value
            }
        }
        if (found != null) onHit() else onMiss()
        return found
    }

    fun put(key: K, value: V, ttlMs: Long = defaultTtlMs) {
        val expiresAt = clockMs() + ttlMs.coerceAtLeast(500L)
        synchronized(lock) {
            store[key] = CachedItem(value = value, expiresAtMs = expiresAt)
            _stats.value = _stats.value.copy(cachedEntries = store.size)
        }
    }

    fun invalidate(key: K) {
        synchronized(lock) {
            store.remove(key)
            _stats.value = _stats.value.copy(cachedEntries = store.size)
        }
    }

    fun invalidatePrefix(prefix: String) {
        synchronized(lock) {
            val keys = store.keys.filter { it.toString().startsWith(prefix) }
            keys.forEach { store.remove(it) }
            _stats.value = _stats.value.copy(cachedEntries = store.size)
        }
    }

    fun clear() {
        synchronized(lock) {
            store.clear()
            _stats.value = _stats.value.copy(cachedEntries = 0)
        }
    }
}

/**
 * Coalesces concurrent identical in-flight async calls into a single upstream request
 * so sudden bursts never create N-way thundering herds against the database.
 */
class SingleFlightCoordinator(
    private val onCoalesced: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(key: String, loader: suspend () -> T): T {
        val (deferred, isLeader) = mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                existing to false
            } else {
                val created = CompletableDeferred<Any?>()
                inFlight[key] = created
                created to true
            }
        }

        if (!isLeader) {
            onCoalesced()
            return deferred.await() as T
        }

        return try {
            val value = loader()
            deferred.complete(value)
            value
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock {
                if (inFlight[key] === deferred) {
                    inFlight.remove(key)
                }
            }
        }
    }
}

enum class DataReadRoutingMode(val label: String) {
    CACHE_FIRST_WITH_FIRESTORE("Cache-First + Firestore Primary"),
    REPLICA_PROXY_READY("Read-Replica / Edge Proxy Ready"),
}

/**
 * Abstraction separating read routing (TTL Cache + Coalescer + future Read-Replica/Edge HTTP proxy)
 * from strongly-consistent primary Firestore writes.
 */
class DatabaseAccessRouter(
    val cache: TtlResponseCache<String, Any>,
    val singleFlight: SingleFlightCoordinator,
    val edgeBaseUrl: String = "",
) {
    private val _routingMode = MutableStateFlow(
        if (edgeBaseUrl.isNotBlank()) DataReadRoutingMode.REPLICA_PROXY_READY
        else DataReadRoutingMode.CACHE_FIRST_WITH_FIRESTORE
    )
    val routingMode: StateFlow<DataReadRoutingMode> = _routingMode.asStateFlow()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> readWithCache(
        cacheKey: String,
        ttlMs: Long = 60_000L,
        forceRefresh: Boolean = false,
        loader: suspend () -> T,
    ): T {
        if (!forceRefresh) {
            val cached = cache.get(cacheKey)
            if (cached != null) {
                return cached as T
            }
        }
        return singleFlight.execute(cacheKey) {
            if (!forceRefresh) {
                val doubleCheck = cache.get(cacheKey)
                if (doubleCheck != null) return@execute doubleCheck as T
            }
            val loaded = loader()
            cache.put(cacheKey, loaded, ttlMs)
            loaded
        }
    }
}
