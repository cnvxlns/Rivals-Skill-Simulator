package com.example.skillsim.model

import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole

/**
 * 덱 한 벌의 완성된 로스터. 검증을 통과한 값만 담기며 그대로 저장·채점된다.
 *
 * 항상 통째로 읽고 쓰기 때문에 행으로 펼치지 않고 JSON 한 덩어리로 보관한다.
 * 필드를 더할 때는 기본값을 주어야 기존에 저장된 덱을 계속 읽을 수 있다
 * (Jackson은 필드가 없으면 코틀린 기본값을 쓰지만, 있는데 null이면 null을 넣는다).
 */
data class DeckRoster(
    /** 선발 인원. 4~6. */
    val starterCount: Int,
    /** 마무리 인원. 1~2. */
    val closerCount: Int,
    /** 26명 전원. 순서는 의미가 없고 [DeckPlayer.slot]이 자리를 정한다. */
    val players: List<DeckPlayer>,
) {
    /** 중계 인원. 총원이 고정이라 선발·마무리에서 자동으로 정해진다. */
    val relieverCount: Int get() = PITCHER_COUNT - starterCount - closerCount

    companion object {
        /** 타자 정원. 주전 9 + 후보 5. */
        const val BATTER_COUNT = 14

        /** 투수 정원. 게임에서 고정값이며, 중계 정원이 여기서 파생된다. */
        const val PITCHER_COUNT = 12

        const val ROSTER_SIZE = BATTER_COUNT + PITCHER_COUNT
    }
}

/**
 * 로스터의 선수 한 명.
 *
 * @param slot 자리. 주전은 포지션명(`C`, `SS`, `DH` …), 후보는 `BENCH1`~`BENCH5`,
 *   투수는 `SP1`~`SP6` / `RP1`~`RP7` / `CP1`~`CP2`.
 * @param position 채점에 쓰는 포지션. 주전과 투수는 [slot]에서 유도하므로 클라이언트 값을 믿지 않고,
 *   후보만 직접 받는다(후보 포수는 C 전용 스킬을 골라야 한다).
 * @param battingOrder 타순. 주전 9명만 가지며 1~9가 중복 없이 채워진다.
 * @param pitcherSlot 투수 슬롯 번호. [slot]의 숫자 부분이며 조건 게이트(`선발1_2` 등)가 이 값을 본다.
 * @param relieverRole 중계 하위 역할. 중계에만 있고 나머지는 null이다. 점수에는 쓰이지 않는다.
 * @param stats 보유 능력치. 키는 stat_weights.csv의 이름만 허용한다.
 */
data class DeckPlayer(
    val slot: String,
    val position: String,
    val cardType: String,
    val skills: List<DeckSkillSelection>,
    val battingOrder: Int? = null,
    val pitcherSlot: Int? = null,
    val relieverRole: RelieverRole? = null,
    val stats: Map<String, Double> = emptyMap(),
    val throwHand: Handedness? = null,
    val batHand: Handedness? = null,
)

/** 선수가 가진 스킬 하나와 그 레벨. */
data class DeckSkillSelection(
    val skillId: String,
    val level: Int,
)
