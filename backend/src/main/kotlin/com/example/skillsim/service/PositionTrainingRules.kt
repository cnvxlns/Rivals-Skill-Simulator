package com.example.skillsim.service

import com.example.skillsim.dto.SkillLevelBonus
import com.example.skillsim.model.ScoreSkill

/**
 * 포지션 훈련(포훈) — 포지션 슬롯에 붙는 보너스의 규칙.
 *
 * 게임에서 "포지션 특훈"이라고도 부르는 한 시스템이다. 슬롯을 키우면 두 가지가 붙는다.
 * 하나는 능력치 보너스, 다른 하나가 여기서 다루는 **스킬 레벨 보너스**다.
 *
 * 효과는 선수가 아니라 **슬롯**에 붙는다. 그래서 그 자리에 누구를 세우든, 스킬 변경권으로
 * 무엇을 새로 뽑든, 보너스 목록에 있는 스킬이면 레벨이 오른다.
 *
 * 공지에서 확인한 것(2024-06-28 v2.03.00 도입, 플레이북 board/23/6425):
 * - 레벨 6·12·20에서 스킬 하나씩, 슬롯당 최대 [MAX_BONUSES]개. 중복 스킬은 나오지 않는다.
 * - 오르는 폭은 [BONUS_RANGE]. +1과 +2를 가르는 확률은 공개된 적이 없다.
 * - 모먼트 전용 스킬과 HOF 티어는 보너스로 나오지 않는다(board/9/9032, 9/10742).
 * - 2025-10-30 16차 Live부터 그 포지션에 맞는 스킬만 나온다(board/10/12633).
 *
 * 블랙·WBC 티어는 나온다는 문장도 안 나온다는 문장도 없다. 근거 없이 열면 점수가 조용히
 * 부풀기 때문에 [ELIGIBLE_TIERS]에서 뺐다. 공지가 확인되면 여기만 고치면 된다.
 */
internal object PositionTrainingRules {

    /** 슬롯 하나가 가질 수 있는 스킬 레벨 보너스의 수. 레벨 6·12·20에서 하나씩 얻는다. */
    const val MAX_BONUSES = 3

    /** 한 스킬이 오르는 폭. */
    val BONUS_RANGE = 1..2

    /** 보너스가 붙을 수 있는 티어. */
    val ELIGIBLE_TIERS = setOf(SkillTier.IRON, SkillTier.BRONZE, SkillTier.SILVER, SkillTier.GOLD)

    fun isEligible(skill: ScoreSkill): Boolean = SkillTier.of(skill) in ELIGIBLE_TIERS

    /**
     * 요청으로 들어온 보너스 목록을 검증해 `스킬 → 오르는 폭`으로 바꾼다.
     *
     * 규칙을 어기면 [IllegalArgumentException]을 던진다. 호출부가 이를 400으로 옮긴다.
     * 목록이 비어 있으면 빈 map이며, 그때는 채점이 지금까지와 완전히 같다.
     *
     * @param position 이 슬롯의 포지션. 맞지 않는 스킬은 게임에서 나오지 않으므로 거절한다.
     * @param findSkill 스킬 조회. 저장소를 직접 알지 않으려고 함수로 받는다.
     */
    fun resolve(
        bonuses: List<SkillLevelBonus>?,
        position: String,
        findSkill: (String) -> ScoreSkill?,
    ): Map<String, Int> {
        val entries = bonuses.orEmpty()
        if (entries.isEmpty()) {
            return emptyMap()
        }
        require(entries.size <= MAX_BONUSES) {
            "A position slot can have at most $MAX_BONUSES position training bonuses."
        }

        val resolved = LinkedHashMap<String, Int>()
        for (entry in entries) {
            val skillId = entry.skillId?.trim().orEmpty()
            require(skillId.isNotEmpty()) { "Position training bonus requires a skill." }
            require(resolved.put(skillId, 0) == null) {
                "Duplicate position training bonus is not allowed: $skillId"
            }

            val bonus = entry.bonus ?: 0
            require(bonus in BONUS_RANGE) {
                "Position training bonus must be between ${BONUS_RANGE.first} and ${BONUS_RANGE.last}."
            }
            val skill = findSkill(skillId)
                ?: throw IllegalArgumentException("Score skill not found: $skillId")
            require(isEligible(skill)) {
                "Position training does not grant a level bonus to $skillId."
            }
            require(SkillRules.matchesPosition(skill.position, position)) {
                "Position training bonus $skillId does not match requested position."
            }
            resolved[skillId] = bonus
        }
        return resolved
    }
}
