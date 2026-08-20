package tv.ororo.app

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import tv.ororo.app.data.api.TmdbApi

class TmdbApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: TmdbApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/3/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `weekly trending endpoint is paged and decoded`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"page":2,"results":[{"id":603}],"total_pages":5}"""
            )
        )

        val response = api.getTrendingMovies(timeWindow = "week", page = 2)

        assertEquals(listOf(603), response.results.map { it.id })
        assertEquals("/3/trending/movie/week?page=2&language=en-US", server.takeRequest().path)
    }

    @Test
    fun `movie details endpoint exposes IMDb id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":603,"imdb_id":"tt0133093","title":"The Matrix"}"""
            )
        )

        val response = api.getMovieDetails(movieId = 603)

        assertEquals("tt0133093", response.imdbId)
        assertEquals("/3/movie/603?language=en-US", server.takeRequest().path)
    }

    @Test
    fun `weekly trending TV endpoint is paged and decoded`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"page":3,"results":[{"id":1399}],"total_pages":5}"""
            )
        )

        val response = api.getTrendingShows(timeWindow = "week", page = 3)

        assertEquals(listOf(1399), response.results.map { it.id })
        assertEquals("/3/trending/tv/week?page=3&language=en-US", server.takeRequest().path)
    }

    @Test
    fun `TV external ids endpoint exposes IMDb id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":1399,"imdb_id":"tt0944947","tvdb_id":121361}"""
            )
        )

        val response = api.getTvExternalIds(seriesId = 1399)

        assertEquals("tt0944947", response.imdbId)
        assertEquals("/3/tv/1399/external_ids", server.takeRequest().path)
    }
}
