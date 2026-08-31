package com.example.skillsim.service

import com.example.skillsim.model.ScoreSkill
import java.util.Locale

/**
 * 점수표를 나누는 티어.
 *
 * 분류 기준은 `card_type` + `skill_id` 접두사이며,
 * docs/skill_dataset_audit.md에 기록된 티어 산출 규칙과 동일하다.
 */
enum class SkillTier(val key: String) {
    IRON("iron"),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold"),
    HOF("hof"),
    MOMENT("moment"),
    WBC("wbc"),
    BLACK("black"),
    ;

    companion object {
        /** 표시 순서. 낮은 티어부터 올라간다. */
        val DISPLAY_ORDER = listOf(IRON, BRONZE, SILVER, GOLD, HOF, MOMENT, WBC, BLACK)

        /** 분류 불가 시 null을 반환한다. 호출자가 제외 여부를 정한다. */
        fun of(skill: ScoreSkill): SkillTier? {
            when (skill.cardType.uppercase(Locale.ROOT)) {
                "BLACK" -> return BLACK
                "WBC" -> return WBC
                "HOF" -> return HOF
                "MOMENT", "SUPREME_MOMENT" -> return MOMENT
            }
            val id = skill.skillKey.uppercase(Locale.ROOT)
            return when {
                id.startsWith("G_") -> GOLD
                id.startsWith("S_") -> SILVER
                id.startsWith("B_") -> BRONZE
                id.startsWith("I_") -> IRON
                else -> null
            }
        }
    }
}
