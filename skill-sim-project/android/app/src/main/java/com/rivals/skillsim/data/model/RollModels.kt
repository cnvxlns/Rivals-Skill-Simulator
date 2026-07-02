package com.rivals.skillsim.data.model

data class RollRequest(
    val cardType: CardType,
    val ticketType: TicketType,
    val useLevelProtectionSlots: List<Boolean>,
    val lockedSlots: List<Int>,
    val currentSkillIds: List<Long?>,
    val currentGrades: List<Grade>,
    val selectedTheme: String? = null,
    val position: String? = null,
    val subPosition: String? = null,
)

data class RollResponse(
    val slots: List<SkillSlot> = emptyList(),
    val totalScore: Double = 0.0,
)
