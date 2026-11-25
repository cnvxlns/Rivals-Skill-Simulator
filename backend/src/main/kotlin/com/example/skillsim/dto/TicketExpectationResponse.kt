package com.example.skillsim.dto

/**
 * 스킬 변경권을 몇 장쯤 쓰면 지금보다 나아지는가.
 *
 * @param currentTotal 지금 끼워 둔 스킬의 합계 점수. 모든 비교의 기준이다.
 * @param slotOneLockable 이 카드가 첫 슬롯을 잠글 수 있는가. 화면이 토글을 켤지 정하는 데 쓴다.
 */
data class TicketExpectationResponse(
    val currentTotal: Double,
    val slotOneLockable: Boolean,
    val tickets: List<TicketOutcome>,
) {
    /**
     * @param ticket `SKILL_CHANGE` / `PREMIUM_SKILL_CHANGE` / `SUPREME_SKILL_CHANGE`.
     * @param revocable 결과를 무를 수 있는가. 일반 변경권만 false다. 기대 장수가 같아도
     *   이 값이 다르면 위험이 다르다.
     * @param improveChance 한 장으로 지금보다 나아질 확률.
     * @param expectedTickets 나아질 때까지 기대되는 장수. [improveChance]가 0이면 발산하므로
     *   null이고, 화면은 이때 "사실상 불가"로 읽는다.
     * @param averageGain 개선 조합의 반올림 전 점수와 현재 점수 차이의 평균. 개선이 불가능하면 null이다.
     * @param averageTotal 한 장을 썼을 때 나오는 반올림 전 점수의 평균.
     * @param chanceWithin 같은 조건으로 반복 시도할 때 장수별 누적 성공 확률.
     * @param betterCombinations 한 장으로 얻을 수 있는 개선 조합 중 점수가 가장 높은 조합들.
     */
    data class TicketOutcome(
        val ticket: String,
        val revocable: Boolean,
        val improveChance: Double,
        val expectedTickets: Double?,
        val averageGain: Double?,
        val averageTotal: Double,
        val chanceWithin: Map<Int, Double>,
        val betterCombinations: List<BetterCombination> = emptyList(),
    )

    data class BetterCombination(
        val slots: List<CombinationSlot>,
        val total: Double,
        val gain: Double,
        val chance: Double,
    )

    /** level은 포지션특훈 보너스를 적용하기 전의 기본 레벨이다. */
    data class CombinationSlot(
        val skillId: String?,
        val name: String?,
        val level: String,
    )
}
