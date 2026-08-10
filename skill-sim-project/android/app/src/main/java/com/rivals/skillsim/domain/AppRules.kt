package com.rivals.skillsim.domain

import com.rivals.skillsim.data.model.CardType

object AppRules {
    private const val BaseSlotCount = 3
    private const val BlackSlotCount = 4

    fun slotCountFor(cardType: CardType): Int =
        when (cardType) {
            CardType.SIGNATURE_BLACK,
            CardType.WBC_SIGNATURE_BLACK -> BlackSlotCount
            CardType.SIGNATURE,
            CardType.WBC,
            CardType.HOF,
            CardType.MOMENT,
            CardType.SUPREME_MOMENT -> BaseSlotCount
        }
}
