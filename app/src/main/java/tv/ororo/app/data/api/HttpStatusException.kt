package tv.ororo.app.data.api

/**
 * Typed exception carrying the actual HTTP status code so consumers
 * can branch on [code] instead of parsing strings.
 */
class HttpStatusException(
    val code: Int,
    override val message: String = "HTTP $code"
) : Exception(message)
