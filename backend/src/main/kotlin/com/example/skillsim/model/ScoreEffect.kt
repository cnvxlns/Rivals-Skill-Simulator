package com.example.skillsim.model

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * 스킬 하나가 특정 조건에서 특정 스탯에 주는 효과.
 *
 * @param values 등급 사다리. `"1/2/3"`처럼 `/`로 구분된 단계별 수치다.
 * @param baseStat 비율형 효과의 기준 스탯. null이면 [values]가 절대치다.
 */
data class ScoreEffect(
    var id: Long? = null,
    val stat: String,
    val condition: String,
    val values: String,
    val baseStat: String? = null,
    /** 부모 참조. 직렬화하면 순환하므로 제외한다. */
    @get:JsonIgnore
    var skill: ScoreSkill? = null,
)
