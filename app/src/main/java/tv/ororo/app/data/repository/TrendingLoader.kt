package tv.ororo.app.data.repository

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal data class TrendingResult<T>(val items: List<T>, val rankedCount: Int, val isStale: Boolean)
internal data class TrendingPage(val ids: List<Int>, val totalPages: Int)

@Serializable
internal data class TrendingEntry(
    val rank: Int,
    @SerialName("tmdb_id") val tmdbId: Int,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("lookup_completed") val lookupCompleted: Boolean = true
)

@Serializable
private data class TrendingPayload<T>(
    @SerialName("fetched_at_epoch_ms") val fetchedAt: Long,
    val entries: List<TrendingEntry>,
    @SerialName("resolved_imdb_ids") val resolvedIds: Map<Int, String> = emptyMap(),
    val snapshot: List<T>? = null,
    @SerialName("catalog_fetched_at_epoch_ms") val catalogFetchedAt: Long = 0,
    @SerialName("account_scope") val accountScope: String? = null
)

/** Shared by movie and TV repositories so persistence, cancellation and throttling behave alike. */
internal class TrendingLoader<C, T>(
    private val file: File,
    private val json: Json,
    serializer: KSerializer<T>,
    private val limiter: TmdbRequestLimiter,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val accountScope: suspend () -> String?,
    private val now: () -> Long,
    private val configured: () -> Boolean,
    private val catalog: suspend () -> List<C>,
    private val catalogFetchedAt: () -> Long,
    private val page: suspend (Int) -> TrendingPage,
    private val imdbId: suspend (Int) -> String?,
    private val prepareMatch: (List<C>) -> (List<TrendingEntry>) -> List<T>
) {
    private val serializer = TrendingPayload.serializer(serializer)
    private val cacheMutex = Mutex()
    private var memory: TrendingPayload<T>? = null
    private var generation = 0L
    private val flightMutex = Mutex()
    private var flight: Flight<T>? = null

    private data class Updates<T>(
        val cached: TrendingResult<T>? = null,
        val partial: TrendingResult<T>? = null,
        val completed: Int = 0,
        val total: Int = 0
    )

    private class Flight<T>(val owner: String, val updates: MutableStateFlow<Updates<T>>) {
        lateinit var job: Deferred<TrendingResult<T>>
        var users = 0
    }

    suspend fun load(
        force: Boolean,
        onProgress: (Int, Int) -> Unit,
        onCached: (TrendingResult<T>) -> Unit,
        onPartial: (TrendingResult<T>) -> Unit
    ): TrendingResult<T> = coroutineScope {
        val owner = accountScope() ?: throw CancellationException("No active account")
        val shared = flightMutex.withLock {
            (flight?.takeIf { it.job.isActive && it.owner == owner } ?: Flight(owner, MutableStateFlow(Updates<T>())).also { next ->
                flight?.job?.cancel()
                next.job = scope.async(dispatcher, start = CoroutineStart.LAZY) { performLoad(force, owner, next.updates) }
                flight = next
                next.job.start()
            }).also { it.users++ }
        }
        var delivered = Updates<T>()
        suspend fun deliver(update: Updates<T>) {
            if (accountScope() != owner) throw CancellationException("Account changed")
            if (update.cached !== delivered.cached) update.cached?.let(onCached)
            if (update.partial !== delivered.partial) update.partial?.let(onPartial)
            if (update.completed != delivered.completed || update.total != delivered.total) {
                onProgress(update.completed, update.total)
            }
            delivered = update
        }
        val observer = launch(start = CoroutineStart.UNDISPATCHED) { shared.updates.collect { deliver(it) } }
        try {
            shared.job.await().also {
                observer.cancelAndJoin()
                deliver(shared.updates.value)
            }
        } finally {
            observer.cancel()
            withContext(NonCancellable) {
                flightMutex.withLock {
                    shared.users--
                    if (shared.users == 0 && shared.job.isActive) shared.job.cancel()
                }
            }
        }
    }

    suspend fun clear() {
        // Use the same lock order as load registration; no old flight can become a new caller's load.
        flightMutex.withLock {
            cacheMutex.withLock {
                generation++
                memory = null
                withContext(Dispatchers.IO) {
                    file.delete()
                    File(file.parentFile, "${file.name}.tmp").delete()
                }
            }
            flight?.job?.cancel()
            flight = null
        }
    }

    private suspend fun performLoad(force: Boolean, owner: String, updates: MutableStateFlow<Updates<T>>): TrendingResult<T> {
        val (version, cached) = cacheMutex.withLock {
            val payload = memory ?: withContext(Dispatchers.IO) {
                if (!file.exists()) null else runCatching {
                    json.decodeFromString(serializer, file.readText())
                }.getOrNull()
            }.also { memory = it }
            generation to payload
        }
        val rankingsFresh = cached != null && fresh(cached.fetchedAt, now(), RANKING_TTL)
        val snapshot = cached?.takeIf { it.accountScope == owner }?.snapshot
        var fallback = snapshot?.let {
            TrendingResult(it, cached.entries.size,
                force || !rankingsFresh || !fresh(cached.catalogFetchedAt, now(), CATALOG_TTL))
        }
        fallback?.let { value -> updates.update { it.copy(cached = value) } }
        if (fallback != null && !fallback.isStale) return fallback

        try {
            return coroutineScope {
                val matcher = async { prepareMatch(catalog()) }
                // Legacy caches contain rankings but no renderable snapshot. Upgrade after catalog loading.
                val legacy = if (snapshot == null && cached != null) launch {
                    val result = TrendingResult(matcher.await()(cached.entries), cached.entries.size, true)
                    updates.update { it.copy(cached = result) }
                } else null
                val entries = if (!force && rankingsFresh) cached!!.entries else {
                    if (!configured()) throw TmdbConfigurationException()
                    refresh(cached, matcher, updates, allowPartial = cached == null)
                }
                val items = matcher.await()(entries)
                legacy?.join()
                val fetchedAt = if (!force && rankingsFresh) cached!!.fetchedAt else now()
                val mappings = cached?.resolvedIds.orEmpty() + cached?.entries.orEmpty().successfulIds() +
                    entries.successfulIds()
                val payload = TrendingPayload(fetchedAt, entries, mappings, items, catalogFetchedAt(), owner)
                ensureCurrent(owner, version)
                write(payload, version)
                ensureCurrent(owner, version)
                updates.update { it.copy(completed = entries.size, total = entries.size) }
                TrendingResult(items, entries.size, false)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ensureCurrent(owner, version)
            fallback = fallback ?: updates.value.cached ?: updates.value.partial
            return fallback?.copy(isStale = true) ?: throw error
        }
    }

    private suspend fun refresh(
        cached: TrendingPayload<T>?,
        matcher: Deferred<(List<TrendingEntry>) -> List<T>>,
        updates: MutableStateFlow<Updates<T>>,
        allowPartial: Boolean
    ): List<TrendingEntry> = coroutineScope {
        val known = cached?.resolvedIds.orEmpty() + cached?.entries.orEmpty().successfulIds()
        val first = request { page(1) }
        if (first.ids.isEmpty()) throw IOException("TMDB returned no trending titles")
        val pageCount = if (first.totalPages > 0) minOf(5, first.totalPages) else 5
        val remaining = (2..pageCount).associateWith { number -> async {
            try {
                Result.success(request { page(number) })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Apply failures in page order, after publishing any earlier completed matches.
                Result.failure(error)
            }
        } }
        val pending = Channel<Deferred<TrendingEntry>>(Channel.UNLIMITED)
        updates.update { it.copy(total = if (pageCount == 1) minOf(100, first.ids.size) else 100) }
        launch {
            val seen = linkedSetOf<Int>()
            for (number in 1..pageCount) {
                val next = if (number == 1) Result.success(first) else remaining.getValue(number).await()
                if (next.isFailure) {
                    pending.close(next.exceptionOrNull())
                    remaining.values.forEach { if (it.isActive) it.cancel() }
                    return@launch
                }
                val response = next.getOrThrow()
                if (response.ids.isEmpty()) break
                for (id in response.ids) {
                    if (seen.size >= 100) break
                    if (!seen.add(id)) continue
                    val rank = seen.size
                    pending.send(async {
                        known[id]?.let { TrendingEntry(rank, id, it) } ?: resolve(rank, id)
                    })
                }
                if (seen.size >= 100) break
            }
            remaining.values.forEach { if (it.isActive) it.cancel() }
            pending.close()
        }
        val entries = mutableListOf<TrendingEntry>()
        for (lookup in pending) {
            entries += lookup.await()
            // Await in rank order: later completions cannot insert cards above a user's current focus.
            if (allowPartial) {
                val items = matcher.await()(entries)
                if (items.isNotEmpty()) updates.update {
                    it.copy(partial = TrendingResult(items, entries.size, false))
                }
            }
            updates.update { it.copy(completed = entries.size) }
        }
        if (entries.any { !it.lookupCompleted } || entries.none { it.imdbId != null }) {
            throw IOException("TMDB title matching incomplete")
        }
        entries
    }

    private suspend fun resolve(rank: Int, id: Int): TrendingEntry = try {
        TrendingEntry(rank, id, TrendingMoviesRepository.normalizeImdbId(request { imdbId(id) }))
    } catch (error: HttpException) {
        if (error.code() == 401 || error.code() == 403) throw error
        TrendingEntry(rank, id, lookupCompleted = error.code() == 404)
    } catch (_: IOException) {
        TrendingEntry(rank, id, lookupCompleted = false)
    }

    private suspend fun <R> request(block: suspend () -> R): R {
        repeat(3) { attempt ->
            try {
                return limiter.execute(block)
            } catch (error: HttpException) {
                if (attempt == 2 || (error.code() != 429 && error.code() < 500)) throw error
                delay(limiter.retryDelayMs(error, attempt))
            } catch (error: IOException) {
                if (attempt == 2) throw error
                delay(1_000L * (attempt + 1))
            }
        }
        error("Unreachable")
    }

    private suspend fun ensureCurrent(owner: String, version: Long) {
        currentCoroutineContext().ensureActive()
        if (accountScope() != owner || cacheMutex.withLock { generation != version }) {
            throw CancellationException("Trending cache invalidated")
        }
    }

    private suspend fun write(payload: TrendingPayload<T>, version: Long) = cacheMutex.withLock {
        if (generation != version || accountScope() != payload.accountScope) {
            throw CancellationException("Trending cache invalidated")
        }
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            try {
                temporary.writeText(json.encodeToString(serializer, payload))
                currentCoroutineContext().ensureActive()
                if (!temporary.renameTo(file)) throw IOException("Could not replace trending cache")
            } finally {
                temporary.delete()
            }
        }
        memory = payload
    }

    companion object {
        const val RANKING_TTL = 24 * 60 * 60 * 1_000L
        const val CATALOG_TTL = 15 * 60 * 1_000L
        fun fresh(fetchedAt: Long, now: Long, ttl: Long) = now - fetchedAt in 0..ttl
    }
}

private fun List<TrendingEntry>.successfulIds(): Map<Int, String> = mapNotNull { entry ->
    entry.imdbId?.takeIf { entry.lookupCompleted }?.let { entry.tmdbId to it }
}.toMap()
