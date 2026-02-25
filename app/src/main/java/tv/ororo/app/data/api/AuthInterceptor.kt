package tv.ororo.app.data.api

import android.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import tv.ororo.app.data.repository.SessionRepository
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sessionRepository: SessionRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = runBlocking {
            sessionRepository.getCredentials()
        }

        val request = if (credentials != null) {
            val basic = Base64.encodeToString(
                "${credentials.first}:${credentials.second}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            chain.request().newBuilder()
                .addHeader("Authorization", "Basic $basic")
                .addHeader("Accept", "application/json")
                .build()
        } else {
            chain.request().newBuilder()
                .addHeader("Accept", "application/json")
                .build()
        }

        return chain.proceed(request)
    }
}
