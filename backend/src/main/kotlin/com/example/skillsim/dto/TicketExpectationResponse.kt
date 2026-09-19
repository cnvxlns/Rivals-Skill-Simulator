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
     * @param averageGain 나아졌을 때의 평균 상승폭. 나아진 적이 없으면 null이다.
     * @param averageTotal 한 장을 썼을 때 나오는 합계의 평균. 지금보다 낮으면 손해 쪽이 크다는 뜻이다.
     * @param chanceWithin 장수별 누적 성공 확률. 평균만으로는 운이 나쁠 때를 알 수 없어 함께 준다.
     */
    data class TicketOutcome(
        val ticket: String,
        val revocable: Boolean,
        val improveChance: Double,
        val expectedTickets: Double?,
        val averageGain: Double?,
        val averageTotal: Double,
        val chanceWithin: Map<Int, Double>,
    )
}
