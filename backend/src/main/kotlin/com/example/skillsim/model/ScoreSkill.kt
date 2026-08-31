package com.example.skillsim.model

/**
 * CSV에서 적재한 스킬 한 건.
 *
 * @param id CSV에는 없는 값이다. 적재 시 리포지토리가 순번을 부여한다.
 */
data class ScoreSkill(
    var id: Long? = null,
    val skillKey: String,
    val cardType: String,
    val position: String,
    val name: String,
    val description: String,
    val effects: MutableList<ScoreEffect> = mutableListOf(),
)
