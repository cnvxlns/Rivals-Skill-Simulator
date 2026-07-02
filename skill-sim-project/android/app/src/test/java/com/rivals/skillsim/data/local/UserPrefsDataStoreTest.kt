package com.rivals.skillsim.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.rivals.skillsim.data.model.CardType
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class UserPrefsDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun preferencesRoundTripLanguageSettingsAndUserStats() = runTest {
        val store = UserPrefsDataStore(createDataStore())

        store.saveLanguage("EN")
        store.saveCalculatorSettings(
            CalculatorSettings(
                cardType = CardType.WBC_SIGNATURE_BLACK,
                position = "BATTER",
                subPosition = "CF",
                battingOrder = 3,
            ),
        )
        store.saveUserStats(
            mapOf(
                "파워" to 135.0,
                "스페셜덱" to 510.0,
            ),
        )

        val snapshot = store.preferences.first()

        assertEquals("EN", snapshot.languageCode)
        assertEquals(CardType.WBC_SIGNATURE_BLACK, snapshot.calculatorSettings.cardType)
        assertEquals("BATTER", snapshot.calculatorSettings.position)
        assertEquals("CF", snapshot.calculatorSettings.subPosition)
        assertEquals(3, snapshot.calculatorSettings.battingOrder)
        assertEquals(135.0, snapshot.userStats.getValue("파워"), 0.0001)
        assertEquals(510.0, snapshot.userStats.getValue("스페셜덱"), 0.0001)
    }

    private fun TestScope.createDataStore(): DataStore<Preferences> {
        val file = File(temporaryFolder.root, "user.preferences_pb")
        return PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
    }
}
