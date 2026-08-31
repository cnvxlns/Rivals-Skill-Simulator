package com.example.skillsim.repository

import com.example.skillsim.model.ScoreSkill
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import org.springframework.stereotype.Repository

/**
 * CSV에서 적재한 스킬을 메모리에 보관하는 구현체.
 *
 * 적재는 부팅 시 `ScoreDataLoader`가 한 번 수행하고 이후에는 읽기만 발생한다.
 * 읽기 경로가 여러 요청 스레드에서 동시에 호출되므로 조회용 자료구조는 교체 방식으로 갱신한다.
 */
@Repository
class InMemoryScoreSkillRepository : ScoreSkillRepository {

    private val sequence = AtomicLong()

    @Volatile
    private var bySkillKey: Map<String, ScoreSkill> = emptyMap()

    @Volatile
    private var byId: Map<Long, ScoreSkill> = emptyMap()

    @Volatile
    private var byCardType: Map<String, List<ScoreSkill>> = emptyMap()

    override fun findBySkillKey(skillKey: String): ScoreSkill? = bySkillKey[skillKey]

    override fun findByCardTypeIgnoreCase(cardType: String?): List<ScoreSkill> =
        cardType?.let { byCardType[it.uppercase(Locale.ROOT)] } ?: emptyList()

    override fun findById(id: Long?): ScoreSkill? = id?.let { byId[it] }

    override fun findAll(): List<ScoreSkill> = bySkillKey.values.toList()

    override fun deleteAll() {
        bySkillKey = emptyMap()
        byId = emptyMap()
        byCardType = emptyMap()
        sequence.set(0)
    }

    override fun saveAll(skills: Collection<ScoreSkill>) {
        val keyIndex = LinkedHashMap<String, ScoreSkill>()
        val idIndex = LinkedHashMap<Long, ScoreSkill>()
        val cardTypeIndex = LinkedHashMap<String, MutableList<ScoreSkill>>()

        for (skill in skills) {
            if (skill.id == null) {
                skill.id = sequence.incrementAndGet()
            }
            keyIndex[skill.skillKey] = skill
            idIndex[skill.id!!] = skill
            cardTypeIndex.getOrPut(skill.cardType.uppercase(Locale.ROOT)) { mutableListOf() }.add(skill)
        }

        bySkillKey = keyIndex
        byId = idIndex
        byCardType = cardTypeIndex.mapValues { (_, value) -> value.toList() }
    }
}
