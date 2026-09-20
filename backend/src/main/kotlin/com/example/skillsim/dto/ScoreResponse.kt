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
        /** 설명의 x·y·z를 채점에 쓴 레벨 기준 실제 수치로 바꾼 것. 치환할 수 없으면 원문과 같다. */
        val resolvedDescription: String?,
        /** 포지션 훈련이 이 스킬에 얹어 준 레벨. 보너스가 없으면 0이다. */
        val levelBonus: Int,
        /** 보너스까지 반영해 실제로 채점한 등급 라벨(`S2` 등). */
        val appliedGrade: String?,
        val score: Double,
        val perStat: List<StatScore>,
        val breakdown: List<EffectBreakdown>,
        val warnings: List<String>,
        /**
         * 이 점수가 어디서 왔는가. `ENGINE`이 기본이고, 덱 채점에서 워크북 점수표를 쓰면
         * `EXCEL`이다. 표에 없는 스킬·레벨은 `ENGINE`으로 떨어진다.
         */
        val source: String = SOURCE_ENGINE,
        /** 워크북 점수표에서 고른 옵션 변형. 표를 쓰지 않았으면 null이다. */
        val option: String? = null,
        /** 변형이 여럿인데 상황으로 판정할 수 없어 사용자가 골라야 하는가. */
        val optionNeeded: Boolean = false,
    )

    companion object {
        const val SOURCE_ENGINE = "ENGINE"
        const val SOURCE_EXCEL = "EXCEL"
    }

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
