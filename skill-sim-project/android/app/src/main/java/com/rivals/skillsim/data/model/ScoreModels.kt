package com.rivals.skillsim.data.model

data class ScoreSkillOption(
    val skillId: String = "",
    val cardType: String = "",
    val position: String = "",
    val name: String = "",
    val description: String? = null,
    val maxLevel: Int = 0,
    val levelLabels: List<String> = emptyList(),
)

data class ScoreSelection(
    val skillId: String,
    val level: Int,
)

data class ScoreRequest(
    val cardType: String,
    val position: String,
    val selections: List<ScoreSelection>,
    val battingOrder: Int? = null,
    val userStats: Map<String, Double>? = null,
)

data class ScoreStatBreakdown(
    val stat: String = "",
    val value: Double = 0.0,
)

data class ScoreEffectBreakdown(
    val stat: String = "",
    val condition: String = "",
    val weight: Double = 0.0,
    val value: Double = 0.0,
    val conditionProbability: Double = 0.0,
    val subtotal: Double = 0.0,
    val baseStat: String? = null,
    val baseValue: Double? = null,
    val rawValue: Double = 0.0,
)

data class ScoreSkillBreakdown(
    val skillId: String = "",
    val name: String = "",
    val score: Double = 0.0,
    val perStat: List<ScoreStatBreakdown> = emptyList(),
    val breakdown: List<ScoreEffectBreakdown>? = null,
    val warnings: List<String>? = null,
)

data class ScoreResponse(
    val total: Double = 0.0,
    val perSkill: List<ScoreSkillBreakdown> = emptyList(),
    val perStat: List<ScoreStatBreakdown> = emptyList(),
    val warnings: List<String>? = null,
)
