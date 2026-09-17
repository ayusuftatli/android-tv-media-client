package tv.ororo.app.data.repository

import android.os.SystemClock
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore

@Singleton
class TmdbRequestLimiter @Inject constructor() {
    private val requestMutex = Mutex()
    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val requestStarts = ArrayDeque<Long>()
    private var nextRequestAtElapsedMs = 0L

    suspend fun <T> execute(block: suspend () -> T): T {
        requestSemaphore.acquire()
        return try {
            awaitRequestSlot()
            block()
        } finally {
            requestSemaphore.release()
        }
    }

    private suspend fun awaitRequestSlot() {
        requestMutex.lock()
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
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
                val delayMs = maxOf(spacingDelayMs, windowDelayMs)

                if (delayMs > 0L) {
                    delay(delayMs)
                    continue
                }

                val requestStart = SystemClock.elapsedRealtime()
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

    companion object {
        internal const val MAX_REQUESTS_PER_SECOND = 38
        internal const val REQUEST_INTERVAL_MS = 1_000L / MAX_REQUESTS_PER_SECOND
        private const val RATE_WINDOW_MS = 1_000L
        private const val MAX_CONCURRENT_REQUESTS = 4
    }
}
