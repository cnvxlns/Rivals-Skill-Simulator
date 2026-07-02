package com.rivals.skillsim.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rivals.skillsim.data.model.CardType
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class CalculatorSettings(
    val cardType: CardType = CardType.SIGNATURE,
    val position: String = "BATTER",
    val subPosition: String = "ALL",
    val battingOrder: Int? = null,
)

data class UserPrefsSnapshot(
    val languageCode: String = "KR",
    val calculatorSettings: CalculatorSettings = CalculatorSettings(),
    val userStats: Map<String, Double> = emptyMap(),
)

interface UserPrefsStore {
    val preferences: Flow<UserPrefsSnapshot>

    suspend fun saveLanguage(languageCode: String)

    suspend fun saveCalculatorSettings(settings: CalculatorSettings)

    suspend fun saveUserStats(userStats: Map<String, Double>)
}

class UserPrefsDataStore(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson(),
) : UserPrefsStore {
    override val preferences: Flow<UserPrefsSnapshot> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map(::toSnapshot)

    override suspend fun saveLanguage(languageCode: String) {
        dataStore.edit { preferences ->
            preferences[Keys.LanguageCode] = languageCode
        }
    }

    override suspend fun saveCalculatorSettings(settings: CalculatorSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.CardType] = settings.cardType.name
            preferences[Keys.Position] = settings.position
            preferences[Keys.SubPosition] = settings.subPosition
            if (settings.battingOrder == null) {
                preferences.remove(Keys.BattingOrder)
            } else {
                preferences[Keys.BattingOrder] = settings.battingOrder
            }
        }
    }

    override suspend fun saveUserStats(userStats: Map<String, Double>) {
        dataStore.edit { preferences ->
            preferences[Keys.UserStatsJson] = gson.toJson(userStats)
        }
    }

    private fun toSnapshot(preferences: Preferences): UserPrefsSnapshot =
        UserPrefsSnapshot(
            languageCode = preferences[Keys.LanguageCode] ?: "KR",
            calculatorSettings = CalculatorSettings(
                cardType = preferences[Keys.CardType].toCardTypeOrDefault(),
                position = preferences[Keys.Position] ?: "BATTER",
                subPosition = preferences[Keys.SubPosition] ?: "ALL",
                battingOrder = preferences[Keys.BattingOrder],
            ),
            userStats = decodeUserStats(preferences[Keys.UserStatsJson]),
        )

    private fun String?.toCardTypeOrDefault(): CardType =
        this?.let { value ->
            runCatching { CardType.valueOf(value) }.getOrNull()
        } ?: CardType.SIGNATURE

    private fun decodeUserStats(json: String?): Map<String, Double> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<Map<String, Double>>() {}.type
            gson.fromJson<Map<String, Double>>(json, type) ?: emptyMap()
        }.getOrDefault(emptyMap())
    }

    private object Keys {
        val LanguageCode = stringPreferencesKey("language_code")
        val CardType = stringPreferencesKey("calculator_card_type")
        val Position = stringPreferencesKey("calculator_position")
        val SubPosition = stringPreferencesKey("calculator_sub_position")
        val BattingOrder = intPreferencesKey("calculator_batting_order")
        val UserStatsJson = stringPreferencesKey("calculator_user_stats_json")
    }
}
