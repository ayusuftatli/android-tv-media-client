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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val emailKey = stringPreferencesKey("email")
    private val passwordKey = stringPreferencesKey("password")

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[emailKey] != null && prefs[passwordKey] != null
    }

    suspend fun saveCredentials(email: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[emailKey] = email
            prefs[passwordKey] = password
        }
    }

    suspend fun getCredentials(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val email = prefs[emailKey] ?: return null
        val password = prefs[passwordKey] ?: return null
        return Pair(email, password)
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}
