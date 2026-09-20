package com.example.skillsim.service

import com.example.skillsim.model.DeckScoreLadder

/**
 * 덱 스코어 보상의 사다리 모양. 상수와 순수 함수만 둔다.
 *
 * [DeckRules]·[CardRules]와 같은 결이다 — 측정값은 CSV에, 판단은 여기에 둔다.
 *
 * 임계값 목록을 CSV가 아니라 여기에 두는 이유가 있다. `deck_score_rewards.csv`에는 **무언가를
 * 주는** 티어만 있다. 팀 330·345처럼 아무것도 주지 않는 칸도 화면에는 나와야 하고, 사용자가
 * 고를 수도 있다. 그래서 사다리 자체는 규칙이고 보상은 데이터다.
 *
 * 같은 목록이 `tools/validate_deck_data.py`에도 있다. 두 벌이지만 서로를 검사한다 — CSV에
 * 여기 없는 임계값이 있으면 파이썬 쪽이 잡고, 반대는 `DeckScoreRulesTest`가 잡는다.
 */
internal object DeckScoreRules {

    /** 팀 덱 스코어 25칸. */
    val TEAM_THRESHOLDS = listOf(
        200, 240, 260, 280, 300, 315, 330, 345, 360, 375, 390, 405, 420, 435,
        450, 460, 470, 480, 490, 500, 520, 540, 560, 580, 600,
    )

    /** 스페셜 덱 스코어 29칸. */
    val SPECIAL_THRESHOLDS = listOf(
        100, 150, 200, 220, 240, 260, 280, 300, 320, 340, 360, 380, 400, 420, 440,
        460, 480, 500, 520, 540, 560, 580, 600, 615, 630, 645, 660, 680, 700,
    )

    /**
     * 연대를 고르는 칸.
     *
     * 워크북은 680(`AY37`)의 입력을 `"O"`로 검증하지만 수식은 연도를 본다. 수식을 따른다 —
     * `"O"`를 넣으면 `연도 - "O"`가 되어 엑셀 안에서 #VALUE!가 난다. 검증 쪽이 틀린 것이다.
     */
    val DECADE_TIERS = setOf(615, 645, 680)

    /** 고를 수 있는 연대. 워크북 드롭다운의 `Years` 목록과 같다. */
    val DECADE_YEARS = (1880..2020 step 10).toList()

    fun thresholdsOf(ladder: DeckScoreLadder): List<Int> = when (ladder) {
        DeckScoreLadder.TEAM -> TEAM_THRESHOLDS
        DeckScoreLadder.SPECIAL -> SPECIAL_THRESHOLDS
    }

    fun isDecadeTier(ladder: DeckScoreLadder, threshold: Int): Boolean =
        ladder == DeckScoreLadder.SPECIAL && threshold in DECADE_TIERS

    /** 초월 레벨의 사다리 전체 범위. 카드별 실제 상한은 표가 정한다. */
    val TRANSCENDENCE_LEVELS = 0..15

    /** 강화 레벨의 사다리 전체 범위. */
    val ENHANCEMENT_LEVELS = 1..20

    /** 카드 연도로 받아들이는 범위. 워크북 목록은 1880부터다. */
    val CARD_YEARS = 1870..2100
}
