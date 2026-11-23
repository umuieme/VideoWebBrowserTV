package com.umuieme.videowebbrowser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Top-level property delegate: creates at most one DataStore per process for the file
 * "settings.preferences_pb". Two DataStore instances on the same file would throw, which is
 * why this lives at file level rather than inside the class.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings backed by DataStore Preferences. Reads are exposed as Flows so the UI updates
 * automatically when a value changes; writes are suspend functions that run off the main thread.
 */
class SettingsRepository(context: Context) {

    // Hold the application context only; an Activity context here would leak the Activity.
    private val dataStore = context.applicationContext.settingsDataStore

    // An unreadable file means "no settings", which sends the user back to setup instead of
    // crashing. Any other exception is a bug and is rethrown.
    private val prefs: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    /** The site opened at launch, or null until the user completes first-run setup. */
    val homeUrl: Flow<String?> = prefs
        .map { prefs -> prefs[Keys.HOME_URL] }
        .distinctUntilChanged()

    /**
     * Registrable domains the user chose "Always allow" for (see DomainUtils). Navigations to
     * them never ask, and the ad blocker never blocks them.
     */
    val allowedDomains: Flow<Set<String>> = prefs
        .map { prefs -> prefs[Keys.ALLOWED_DOMAINS].orEmpty() }
        .distinctUntilChanged()

    /** Persists [url]. It must already be normalized (see UrlNormalizer). */
    suspend fun saveHomeUrl(url: String) {
        dataStore.edit { prefs -> prefs[Keys.HOME_URL] = url }
    }

    /** Adds a registrable domain to the allowlist. edit() is atomic, so concurrent adds don't lose entries. */
    suspend fun addAllowedDomain(domain: String) {
        dataStore.edit { prefs -> prefs[Keys.ALLOWED_DOMAINS] = prefs[Keys.ALLOWED_DOMAINS].orEmpty() + domain }
    }

    /** Removes a domain from the allowlist; used by the settings screen (Milestone 7). */
    suspend fun removeAllowedDomain(domain: String) {
        dataStore.edit { prefs -> prefs[Keys.ALLOWED_DOMAINS] = prefs[Keys.ALLOWED_DOMAINS].orEmpty() - domain }
    }

    private object Keys {
        val HOME_URL = stringPreferencesKey("home_url")
        val ALLOWED_DOMAINS = stringSetPreferencesKey("allowed_domains")
    }
}
