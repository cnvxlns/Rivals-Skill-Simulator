package com.example.skillsim.dto

/**
 * 덱 편집기가 화면을 그리는 데 필요한 규칙.
 *
 * 앱이 사다리와 상한을 복제하지 않게 한다. 복제하면 게임이 바뀔 때 두 곳을 고쳐야 하고,
 * 한쪽만 고치면 화면과 채점이 조용히 어긋난다.
 *
 * @param partWeights 파트별 종합 점수 가중치. @param partScale 파트 평균에 곱하는 수.
 */
data class DeckRulesResponse(
    val ladders: List<Ladder>,
    val decadeYears: List<Int>,
    val growth: List<GrowthLimit>,
    val partWeights: Map<String, Double>,
    val partScale: Double,
) {
    /**
     * @param tiers 임계값 전체. 보상이 없는 칸도 화면에는 나와야 한다.
     */
    data class Ladder(
        /** `TEAM` 또는 `SPECIAL`. */
        val ladder: String,
        val tiers: List<Tier>,
    ) {
        /**
         * @param decade 연대를 골라야 하는 칸인가.
         * @param left 좌를 골랐을 때의 효과 요약. @param right 우를 골랐을 때.
         */
        data class Tier(
            val threshold: Int,
            val decade: Boolean,
            val left: List<Effect>,
            val right: List<Effect>,
        )

        /**
         * @param target `BATTER_ALL` 같은 그룹 이름이거나 `1B|3B` 같은 자리 목록.
         * @param condition `ALWAYS`·`ORDER_1_2` 등.
         */
        data class Effect(
            val target: String,
            val stat: String,
            val amount: Int,
            val condition: String,
        )
    }

    /**
     * 카드별 성장 상한.
     *
     * 시그니처의 초월은 9에서, 블랙의 강화는 10에서 끝난다. 화면이 드롭다운을 거기까지만
     * 열어야 사용자가 없는 레벨을 고르지 않는다.
     *
     * @param track `TRANSCENDENCE` 또는 `ENHANCEMENT`.
     */
    data class GrowthLimit(
        val track: String,
        val cardGrade: String,
        val cardVariant: String,
        val maxLevel: Int,
    )
}
