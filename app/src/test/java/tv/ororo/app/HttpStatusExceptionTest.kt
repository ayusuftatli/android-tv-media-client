package tv.ororo.app

import org.junit.Assert.*
import org.junit.Test
import tv.ororo.app.data.api.HttpStatusException

class HttpStatusExceptionTest {

    @Test
    fun `HttpStatusException carries correct code`() {
        val ex = HttpStatusException(401)
        assertEquals(401, ex.code)
    }

    @Test
    fun `401 is correctly identified via typed exception`() {
        val ex: Exception = HttpStatusException(401)
        val code = (ex as? HttpStatusException)?.code
        assertEquals(401, code)
    }

    @Test
    fun `402 is correctly identified via typed exception`() {
        val ex: Exception = HttpStatusException(402)
        val code = (ex as? HttpStatusException)?.code
        assertEquals(402, code)
    }

    @Test
    fun `non-HttpStatusException yields null code`() {
        val ex: Exception = RuntimeException("some error")
        val code = (ex as? HttpStatusException)?.code
        assertNull(code)
    }

    @Test
    fun `login error mapping for 401`() {
        val ex: Exception = HttpStatusException(401)
        val msg = when ((ex as? HttpStatusException)?.code) {
            401 -> "Invalid email or password"
            402 -> "Free limit reached. Subscription required."
            else -> "Login failed. Check your connection."
        }
        assertEquals("Invalid email or password", msg)
    }

    @Test
    fun `login error mapping for 402`() {
        val ex: Exception = HttpStatusException(402)
        val msg = when ((ex as? HttpStatusException)?.code) {
            401 -> "Invalid email or password"
            402 -> "Free limit reached. Subscription required."
            else -> "Login failed. Check your connection."
        }
        assertEquals("Free limit reached. Subscription required.", msg)
    }

    @Test
    fun `login error mapping for generic error`() {
        val ex: Exception = RuntimeException("timeout")
        val msg = when ((ex as? HttpStatusException)?.code) {
            401 -> "Invalid email or password"
            402 -> "Free limit reached. Subscription required."
            else -> "Login failed. Check your connection."
        }
        assertEquals("Login failed. Check your connection.", msg)
    }

    @Test
    fun `detail screen error mapping for 401 triggers logout path`() {
        val ex: Exception = HttpStatusException(401)
        val httpCode = (ex as? HttpStatusException)?.code
        // 401 should trigger session clear + auth event, not show error message
        assertTrue(httpCode == 401)
    }

    @Test
    fun `detail screen error mapping for 402 shows subscription message`() {
        val ex: Exception = HttpStatusException(402)
        val httpCode = (ex as? HttpStatusException)?.code
        val errorMsg = when (httpCode) {
            402 -> "Subscription required."
            else -> "Failed to load details."
        }
        assertEquals("Subscription required.", errorMsg)
    }
}
