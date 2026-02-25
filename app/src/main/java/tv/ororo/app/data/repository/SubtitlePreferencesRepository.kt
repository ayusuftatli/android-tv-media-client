package tv.ororo.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.subtitlePrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitle_preferences")

@Singleton
class SubtitlePreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val subtitlesEnabledKey = booleanPreferencesKey("subtitles_enabled")
    private val preferredSubtitleLangKey = stringPreferencesKey("preferred_subtitle_lang")

    val subtitlesEnabled: Flow<Boolean> = context.subtitlePrefsDataStore.data.map { prefs ->
        prefs[subtitlesEnabledKey] ?: true
    }

    val preferredSubtitleLang: Flow<String> = context.subtitlePrefsDataStore.data.map { prefs ->
        normalizeLanguage(prefs[preferredSubtitleLangKey])
            ?: normalizeLanguage(Locale.getDefault().language)
            ?: "en"
    }

    suspend fun setSubtitlesEnabled(enabled: Boolean) {
        context.subtitlePrefsDataStore.edit { prefs ->
            prefs[subtitlesEnabledKey] = enabled
        }
    }

    suspend fun setPreferredSubtitleLang(lang: String) {
        val normalized = normalizeLanguage(lang) ?: return
        context.subtitlePrefsDataStore.edit { prefs ->
            prefs[preferredSubtitleLangKey] = normalized
            prefs[subtitlesEnabledKey] = true
        }
    }

    suspend fun updateSelection(selectedLanguage: String?) {
        val normalized = normalizeLanguage(selectedLanguage)
        if (normalized == null) {
            setSubtitlesEnabled(false)
            return
        }
        setPreferredSubtitleLang(normalized)
    }

    private fun normalizeLanguage(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return value.substringBefore('-').substringBefore('_').lowercase(Locale.US)
    }
}
