package tv.ororo.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionRepository internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.dataStore)

    private val emailKey = stringPreferencesKey("email")
    private val passwordKey = stringPreferencesKey("password")

    private val cacheScopeKey = stringPreferencesKey("cache_scope")

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[emailKey] != null && prefs[passwordKey] != null
    }

    suspend fun saveCredentials(email: String, password: String) {
        dataStore.edit { prefs ->
            prefs[emailKey] = email
            prefs[passwordKey] = password
            prefs[cacheScopeKey] = UUID.randomUUID().toString()
        }
    }

    suspend fun getCredentials(): Pair<String, String>? {
        val prefs = dataStore.data.first()
        val email = prefs[emailKey] ?: return null
        val password = prefs[passwordKey] ?: return null
        return Pair(email, password)
    }

    // An opaque login identifier survives process restarts but never another login.
    suspend fun getCacheScope(): String? {
        val current = dataStore.data.first()
        if (current[emailKey] == null || current[passwordKey] == null) return null
        current[cacheScopeKey]?.let { return it }
        // Migrate sessions created before trending snapshots existed.
        val updated = dataStore.edit { prefs ->
            if (prefs[emailKey] != null && prefs[passwordKey] != null && prefs[cacheScopeKey] == null) {
                prefs[cacheScopeKey] = UUID.randomUUID().toString()
            }
        }
        return updated[cacheScopeKey]
    }

    suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }
}
