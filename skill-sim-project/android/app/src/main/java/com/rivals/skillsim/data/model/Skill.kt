package com.rivals.skillsim.data.model

data class SkillEffect(
    val condition: String = "",
    val logic: String = "",
    val description: String? = null,
)

data class Skill(
    val id: Long = 0,
    val skillId: String? = null,
    val name: String = "",
    val tier: Tier = Tier.IRON,
    val description: String? = null,
    val position: String? = null,
    val subPositions: String? = null,
    val effects: List<SkillEffect>? = null,
    val levelEffects: Map<String, String>? = null,
)

data class SkillSlot(
    val skill: Skill? = null,
    val grade: Grade = Grade.D,
    val score: Double? = null,
)
