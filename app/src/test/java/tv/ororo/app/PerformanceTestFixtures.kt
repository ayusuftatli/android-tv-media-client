package tv.ororo.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.ororo.app.data.api.OroroApi
import tv.ororo.app.data.model.dto.*

internal class MemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    var activeCollectors = 0
        private set
    var changes = 0
        private set

    override val data: Flow<Preferences> = flow {
        activeCollectors++
        try {
            emitAll(state)
        } finally {
            activeCollectors--
        }
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            if (updated != state.value) changes++
            state.value = updated
            updated
        }
}

internal class FakeOroroApi : OroroApi {
    var movies: suspend () -> MoviesResponse = { MoviesResponse(emptyList()) }
    var shows: suspend () -> ShowsResponse = { ShowsResponse(emptyList()) }
    var show: suspend (Int) -> ShowDto = { ShowDto(it, "Show") }
    var episode: suspend (Int) -> EpisodeDetailDto = { EpisodeDetailDto(it) }
    var movieCalls = 0
        private set
    var episodeCalls = 0
        private set

    override suspend fun getMovies(): MoviesResponse {
        movieCalls++
        return movies()
    }
    override suspend fun getShows() = shows()
    override suspend fun getShow(id: Int) = show(id)
    override suspend fun getEpisode(id: Int): EpisodeDetailDto {
        episodeCalls++
        return episode(id)
    }
    override suspend fun getMovie(id: Int) = MovieDetailDto(id, "Movie")
}
