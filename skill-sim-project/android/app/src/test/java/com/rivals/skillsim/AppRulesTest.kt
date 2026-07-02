package com.rivals.skillsim

import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.domain.AppRules
import org.junit.Assert.assertEquals
import org.junit.Test

class AppRulesTest {
    @Test
    fun signatureBlackCardsUseFourSlots() {
        assertEquals(4, AppRules.slotCountFor(CardType.SIGNATURE_BLACK))
        assertEquals(4, AppRules.slotCountFor(CardType.WBC_SIGNATURE_BLACK))
    }

    @Test
    fun nonBlackCardsUseThreeSlots() {
        assertEquals(3, AppRules.slotCountFor(CardType.SIGNATURE))
        assertEquals(3, AppRules.slotCountFor(CardType.WBC))
        assertEquals(3, AppRules.slotCountFor(CardType.HOF))
        assertEquals(3, AppRules.slotCountFor(CardType.MOMENT))
    }
}
