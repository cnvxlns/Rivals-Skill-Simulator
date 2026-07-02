package com.rivals.skillsim.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsTest {
    @Test
    fun everyLanguageHasSameKeys() {
        val expectedKeys = AppStrings.translations.getValue(LanguageCode.KR).keys

        LanguageCode.entries.forEach { language ->
            assertEquals(expectedKeys, AppStrings.translations.getValue(language).keys)
        }
    }

    @Test
    fun tabLabelsExistForAllFiveLanguages() {
        LanguageCode.entries.forEach { language ->
            assertTrue(AppStrings.t(language, "tab_simulator").isNotBlank())
            assertTrue(AppStrings.t(language, "tab_calculator").isNotBlank())
        }
    }
}
