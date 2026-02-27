package tv.ororo.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.savedContentDataStore: DataStore<Preferences> by preferencesDataStore(name = "saved_content")

@Singleton
class SavedContentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val savedKeysPreference = stringSetPreferencesKey("saved_keys")

    val savedKeysFlow: Flow<Set<String>> = context.savedContentDataStore.data.map { prefs ->
        prefs[savedKeysPreference] ?: emptySet()
    }

    fun isSavedFlow(type: String, id: Int): Flow<Boolean> {
        val key = contentKey(type, id)
        return savedKeysFlow.map { keys -> key in keys }
    }

    suspend fun setSaved(type: String, id: Int, isSaved: Boolean) {
        val key = contentKey(type, id)
        context.savedContentDataStore.edit { prefs ->
            val updated = (prefs[savedKeysPreference] ?: emptySet()).toMutableSet()
            if (isSaved) {
                updated.add(key)
            } else {
                updated.remove(key)
            }
            prefs[savedKeysPreference] = updated
        }
    }

    suspend fun toggleSaved(type: String, id: Int): Boolean {
        val key = contentKey(type, id)
        var savedAfterToggle = false
        context.savedContentDataStore.edit { prefs ->
            val updated = (prefs[savedKeysPreference] ?: emptySet()).toMutableSet()
            savedAfterToggle = if (updated.contains(key)) {
                updated.remove(key)
                false
            } else {
                updated.add(key)
                true
            }
            prefs[savedKeysPreference] = updated
        }
        return savedAfterToggle
    }

    companion object {
        const val TYPE_MOVIE = "movie"
        const val TYPE_SHOW = "show"

        fun contentKey(type: String, id: Int): String {
            return "${type.lowercase(Locale.US)}:$id"
        }

        fun savedIdsForType(savedKeys: Set<String>, type: String): Set<Int> {
            val normalizedType = type.lowercase(Locale.US)
            val prefix = "$normalizedType:"
            return savedKeys.mapNotNull { key ->
                if (!key.startsWith(prefix)) return@mapNotNull null
                key.substringAfter(prefix).toIntOrNull()
            }.toSet()
        }
    }
}
