package com.example.skillsim.service

import com.example.skillsim.dto.PositionTrainingRequest
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.SkillBonus
import com.example.skillsim.model.SlotTraining
import com.example.skillsim.repository.ScoreSkillRepository
import java.util.Locale

/**
 * 포지션 훈련 저장 요청을 검증해 모델로 옮긴다.
 *
 * [DeckValidator]와 같은 결이다. 스프링도 HTTP도 모르고 [IllegalArgumentException]만 던진다.
 */
internal class PositionTrainingValidator(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val allowedStatNames: () -> Set<String>,
) {

    fun validate(request: PositionTrainingRequest?): PositionTraining {
        val slots = request?.slots.orEmpty()
        if (slots.isEmpty()) {
            return PositionTraining.EMPTY
        }

        val statNames = allowedStatNames()
        val validated = LinkedHashMap<String, SlotTraining>()

        for (slotName in DeckRules.ALL_SLOTS) {
            val slot = slots.entries.firstOrNull { it.key.trim().uppercase(Locale.ROOT) == slotName }
                ?: continue
            val stats = validateStats(slot.value.stats, statNames, slotName)
            val skills = PositionTrainingRules
                .resolve(slot.value.skills, positionOf(slotName), scoreSkillRepository::findBySkillKey)
                .map { (skillId, bonus) -> SkillBonus(skillId, bonus) }
            if (stats.isNotEmpty() || skills.isNotEmpty()) {
                validated[slotName] = SlotTraining(stats = stats, skills = skills)
            }
        }

        val unknown = slots.keys.map { it.trim().uppercase(Locale.ROOT) }
            .filterNot { it in DeckRules.ALL_SLOTS }
        require(unknown.isEmpty()) { "Unknown position training slot: ${unknown.first()}" }

        return PositionTraining(validated)
    }

    /**
     * 자리의 포지션. 후보는 자리에서 유도할 수 없지만 타자 정원에 속하므로 타자로 본다.
     *
     * 스킬 레벨 보너스가 그 자리에 나올 수 있는 스킬인지 가리는 데만 쓴다.
     */
    private fun positionOf(slot: String): String = DeckRules.positionForSlot(slot) ?: "BATTER"

    private fun validateStats(
        stats: Map<String, Double>?,
        statNames: Set<String>,
        slot: String,
    ): Map<String, Double> {
        val entries = stats.orEmpty().filterValues { it != 0.0 }
        for ((stat, value) in entries) {
            require(stat in statNames) { "Unknown stat in position training for $slot: $stat" }
            require(value.isFinite() && value in 0.0..MAX_STAT_BONUS) {
                "Position training bonus for $stat must be between 0 and ${MAX_STAT_BONUS.toInt()}."
            }
        }
        return entries
    }

    private companion object {
        /**
         * 능력치 증가치의 상한.
         *
         * 공개된 값 중 가장 큰 것이 3루수 20레벨 누적 +8이다. 오타를 걸러 낼 만큼만 느슨하게
         * 두고, 게임이 표를 올리면 이 값도 같이 올린다.
         */
        const val MAX_STAT_BONUS = 50.0
    }
}
