package tv.ororo.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

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
class WatchProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val keyPrefix = "watch_state_"

    fun watchStatesFlow(): Flow<Map<String, WatchState>> {
        return context.watchProgressDataStore.data.map { prefs ->
            prefs.asMap().mapNotNull { (key, value) ->
                val prefKey = key.name
                if (!prefKey.startsWith(keyPrefix)) return@mapNotNull null
                val raw = value as? String ?: return@mapNotNull null
                parseWatchState(raw)
            }.associateBy { it.contentKey }
        }
    }

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
        val raw = context.watchProgressDataStore.data.first()[key] ?: return null
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

        context.watchProgressDataStore.edit { prefs ->
            prefs[stringPreferencesKey(keyPrefix + contentKey)] = json.encodeToString(watchState)
        }
    }

    suspend fun clearProgress(contentKey: String) {
        context.watchProgressDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(keyPrefix + contentKey))
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
