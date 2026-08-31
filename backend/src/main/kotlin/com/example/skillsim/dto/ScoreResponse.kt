package com.example.skillsim.dto

data class ScoreResponse(
    val total: Double,
    val perSkill: List<SkillScore>,
    val perStat: List<StatScore>,
    val warnings: List<String>,
) {
    data class SkillScore(
        val skillId: String,
        val name: String,
        /** 설명의 x·y·z를 선택한 레벨 기준 실제 수치로 바꾼 것. 치환할 수 없으면 원문과 같다. */
        val resolvedDescription: String?,
        val score: Double,
        val perStat: List<StatScore>,
        val breakdown: List<EffectBreakdown>,
        val warnings: List<String>,
    )

    data class StatScore(
        val stat: String,
        val value: Double,
    )

    data class EffectBreakdown(
        val stat: String,
        val condition: String,
        val weight: Double,
        val value: Double,
        val conditionProbability: Double,
        val subtotal: Double,
        val baseStat: String?,
        val baseValue: Double?,
        val rawValue: Double,
    )
}
