package tv.ororo.app.data.repository

import android.os.SystemClock
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import retrofit2.HttpException

@Singleton
class TmdbRequestLimiter internal constructor(private val elapsedRealtime: () -> Long) {
    @Inject constructor() : this({ SystemClock.elapsedRealtime() })
    private val requestMutex = Mutex()
    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val requestStarts = ArrayDeque<Long>()
    private var nextRequestAtElapsedMs = 0L
    private val cooldownUntil = AtomicLong(0)

    suspend fun <T> execute(block: suspend () -> T): T {
        requestSemaphore.acquire()
        return try {
            awaitRequestSlot()
            block()
        } catch (error: HttpException) {
            if (error.code() == 429) {
                val until = elapsedRealtime() + retryDelayMs(error, 0)
                while (true) {
                    val previous = cooldownUntil.get()
                    if (previous >= until || cooldownUntil.compareAndSet(previous, until)) break
                }
            }
            throw error
        } finally {
            requestSemaphore.release()
        }
    }

    private suspend fun awaitRequestSlot() {
        requestMutex.lock()
        try {
            while (true) {
                val now = elapsedRealtime()
                discardExpiredRequestStarts(now)

                val spacingDelayMs = (nextRequestAtElapsedMs - now).coerceAtLeast(0L)
                val oldestRequestStart = requestStarts.peekFirst()
                val windowDelayMs = if (
                    requestStarts.size >= MAX_REQUESTS_PER_SECOND &&
                    oldestRequestStart != null
                ) {
                    (oldestRequestStart + RATE_WINDOW_MS - now).coerceAtLeast(0L)
                } else {
                    0L
                }
                val cooldownDelayMs = (cooldownUntil.get() - now).coerceAtLeast(0L)
                val delayMs = maxOf(spacingDelayMs, windowDelayMs, cooldownDelayMs)

                if (delayMs > 0L) {
                    delay(delayMs)
                    continue
                }

                val requestStart = elapsedRealtime()
                discardExpiredRequestStarts(requestStart)
                requestStarts.addLast(requestStart)
                nextRequestAtElapsedMs = requestStart + REQUEST_INTERVAL_MS
                return
            }
        } finally {
            requestMutex.unlock()
        }
    }

    private fun discardExpiredRequestStarts(now: Long) {
        while (requestStarts.isNotEmpty()) {
            val oldestRequestStart = requestStarts.peekFirst() ?: return
            if (now - oldestRequestStart < RATE_WINDOW_MS) return
            requestStarts.removeFirst()
        }
    }

    internal fun retryDelayMs(error: HttpException, attempt: Int): Long {
        val header = error.response()?.headers()?.get("Retry-After")
        header?.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1_000 }?.let {
            return it * 1_000L
        }
        if (header != null) {
            val date = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(header)
            }.getOrNull()
            if (date != null) return (date.time - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        return 1_000L * (attempt + 1)
    }

    companion object {
        internal const val MAX_REQUESTS_PER_SECOND = 38
        internal const val REQUEST_INTERVAL_MS = 1_000L / MAX_REQUESTS_PER_SECOND
        private const val RATE_WINDOW_MS = 1_000L
        internal const val MAX_CONCURRENT_REQUESTS = 8
    }
}
