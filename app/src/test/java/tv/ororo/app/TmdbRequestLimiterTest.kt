package tv.ororo.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import tv.ororo.app.data.repository.TmdbRequestLimiter

@OptIn(ExperimentalCoroutinesApi::class)
class TmdbRequestLimiterTest {
    @Test fun `eight simultaneous requests share a rolling 38 per second limit`() = runTest {
        val limiter = TmdbRequestLimiter { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        var active = 0
        var peak = 0
        (1..100).map {
            async {
                limiter.execute {
                    starts += testScheduler.currentTime
                    active++
                    peak = maxOf(peak, active)
                    delay(200)
                    active--
                }
            }
        }.awaitAll()
        assertEquals(8, peak)
        starts.forEach { start -> assertTrue(starts.count { it >= start && it < start + 1_000 } <= 38) }
    }

    @Test fun `429 postpones queued requests across consumers`() = runTest {
        val limiter = TmdbRequestLimiter { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        val first = async {
            try {
                limiter.execute {
                    delay(50)
                    throw tooManyRequests("2")
                }
            } catch (_: HttpException) { }
        }
        val others = (1..20).map {
            async { limiter.execute { starts += testScheduler.currentTime; delay(100) } }
        }
        first.await()
        others.awaitAll()
        assertTrue(starts.any { it >= 2_050 })
        assertTrue(starts.none { it in 50 until 2_050 })
    }

    @Test fun `cancelling a queued request releases its permit`() = runTest {
        val limiter = TmdbRequestLimiter { testScheduler.currentTime }
        val gate = CompletableDeferred<Unit>()
        val jobs = (1..8).map { async { limiter.execute { gate.await() } } }
        advanceTimeBy(500)
        runCurrent()
        val queued = async { limiter.execute { fail("Cancelled request ran") } }
        runCurrent()
        queued.cancelAndJoin()
        gate.complete(Unit)
        jobs.awaitAll()
        assertEquals("ready", limiter.execute { "ready" })
    }

    private fun tooManyRequests(retryAfter: String): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.test/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1).code(429).message("Busy")
            .header("Retry-After", retryAfter).build()
        return HttpException(Response.error<Any>("".toResponseBody(), raw))
    }
}
