package tv.ororo.app.data.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp interceptor that throws [HttpStatusException] for non-2xx responses,
 * giving callers a reliable, typed status code instead of opaque Retrofit errors.
 */
class HttpStatusInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw HttpStatusException(code)
        }
        return response
    }
}
