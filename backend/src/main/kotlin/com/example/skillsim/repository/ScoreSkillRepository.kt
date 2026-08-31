package com.example.skillsim.repository

import com.example.skillsim.model.ScoreSkill

/**
 * 스킬 조회 계약.
 *
 * 데이터 원천은 클래스패스의 CSV이고 사용자 쓰기 경로가 없다. 부팅 시 한 번 적재한 뒤
 * 읽기만 하므로 DB 없이 인메모리로 구현한다([InMemoryScoreSkillRepository]).
 */
interface ScoreSkillRepository {

    fun findBySkillKey(skillKey: String): ScoreSkill?

    fun findByCardTypeIgnoreCase(cardType: String?): List<ScoreSkill>

    fun findById(id: Long?): ScoreSkill?

    fun findAll(): List<ScoreSkill>

    fun deleteAll()

    fun saveAll(skills: Collection<ScoreSkill>)
}
