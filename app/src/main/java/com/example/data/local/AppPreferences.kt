package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "trolley_preferences")

data class DevicePreferences(
    val darkTheme: Boolean = false,
    val discoveryCountry: String = "ZW",
    val consentChoice: String? = null,
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("trolley_theme_dark")
        val DISCOVERY_COUNTRY = stringPreferencesKey("trolley_discovery_country")
        val CONSENT_CHOICE = stringPreferencesKey("trolley_consent")
    }

    val preferencesFlow: Flow<DevicePreferences> = context.dataStore.data.map { prefs ->
        DevicePreferences(
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            discoveryCountry = prefs[Keys.DISCOVERY_COUNTRY] ?: "ZW",
            consentChoice = prefs[Keys.CONSENT_CHOICE],
        )
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun setDiscoveryCountry(countryCode: String) {
        context.dataStore.edit { it[Keys.DISCOVERY_COUNTRY] = countryCode }
    }

    suspend fun setConsentChoice(choice: String) {
        context.dataStore.edit { it[Keys.CONSENT_CHOICE] = choice }
    }
}
