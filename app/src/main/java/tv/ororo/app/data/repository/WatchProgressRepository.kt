package tv.ororo.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tv.ororo.app.di.ApplicationScope
import tv.ororo.app.di.DefaultDispatcher

private val Context.watchProgressDataStore: DataStore<Preferences> by preferencesDataStore(name = "watch_progress")

@Serializable
data class WatchState(
    val contentKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
)

@Singleton
class WatchProgressRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    applicationScope: CoroutineScope,
    defaultDispatcher: CoroutineDispatcher
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json,
        @ApplicationScope applicationScope: CoroutineScope,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ) : this(context.watchProgressDataStore, json, applicationScope, defaultDispatcher)

    private val keyPrefix = "watch_state_"
    private val defaultDispatcher = defaultDispatcher

    private val watchStates = dataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(keyPrefix)) return@mapNotNull null
            parseWatchState(value as? String ?: return@mapNotNull null)
        }.associateBy { it.contentKey }
    }.flowOn(defaultDispatcher)
        .shareIn(applicationScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val watchedMovieIds = watchStates.map { states ->
        states.values.asSequence()
            .filter { it.completed && it.contentKey.startsWith("movie:") }
            .mapNotNull { it.contentKey.substringAfter("movie:").toIntOrNull() }
            .toSet()
    }.distinctUntilChanged()
        .flowOn(defaultDispatcher)
        .shareIn(applicationScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    fun watchStatesFlow(): Flow<Map<String, WatchState>> = watchStates

    fun watchedMovieIdsFlow(): Flow<Set<Int>> = watchedMovieIds

    fun watchStateFlow(contentKey: String): Flow<WatchState?> =
        dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(keyPrefix + contentKey)]?.let(::parseWatchState)
        }.distinctUntilChanged().flowOn(defaultDispatcher)

    fun inProgressWatchStatesFlow(): Flow<List<WatchState>> {
        return watchStatesFlow().map { states ->
            states.values
                .filter { state ->
                    !state.completed && state.positionMs > 0L && state.durationMs > 0L
                }
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun getWatchState(contentKey: String): WatchState? {
        val key = stringPreferencesKey(keyPrefix + contentKey)
        val raw = dataStore.data.first()[key] ?: return null
        return parseWatchState(raw)
    }

    suspend fun saveProgress(
        contentKey: String,
        positionMs: Long,
        durationMs: Long,
        isEnded: Boolean = false
    ) {
        if (positionMs < 0L || durationMs <= 0L) return

        val completed = isCompleted(positionMs, durationMs, isEnded)
        val watchState = WatchState(
            contentKey = contentKey,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed,
            updatedAt = System.currentTimeMillis()
        )

        dataStore.edit { prefs ->
            val key = stringPreferencesKey(keyPrefix + contentKey)
            val previous = prefs[key]?.let(::parseWatchState)
            if (previous?.positionMs == positionMs && previous.durationMs == durationMs &&
                previous.completed == completed
            ) return@edit
            prefs[key] = json.encodeToString(watchState)
        }
    }

    suspend fun clearProgress(contentKey: String) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(keyPrefix + contentKey))
        }
    }

    suspend fun clearAllProgress() {
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys
                .filter { key -> key.name.startsWith(keyPrefix) }
            keysToRemove.forEach { key -> prefs.remove(key) }
        }
    }

    companion object {
        const val COMPLETION_THRESHOLD = 0.95

        fun contentKey(type: String, id: Int): String {
            return when (type.lowercase()) {
                "movie" -> "movie:$id"
                "episode" -> "episode:$id"
                else -> "$type:$id"
            }
        }

        fun isCompleted(positionMs: Long, durationMs: Long, isEnded: Boolean): Boolean {
            if (isEnded) return true
            if (durationMs <= 0L) return false
            return positionMs.toDouble() / durationMs.toDouble() >= COMPLETION_THRESHOLD
        }

        fun parseContentKey(contentKey: String): Pair<String, Int>? {
            val type = contentKey.substringBefore(':', missingDelimiterValue = "").trim()
            val id = contentKey.substringAfter(':', missingDelimiterValue = "").toIntOrNull() ?: return null
            if (type.isBlank()) return null
            return type to id
        }
    }

    private fun parseWatchState(raw: String): WatchState? {
        return try {
            json.decodeFromString<WatchState>(raw)
        } catch (_: Exception) {
            null
        }
    }
}
