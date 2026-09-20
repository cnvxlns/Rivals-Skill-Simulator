package com.example.skillsim.model

import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole
import com.fasterxml.jackson.annotation.JsonIgnore

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
    /**
     * 덱 스코어 보상에서 고른 것들. 임계값마다 좌·우 중 하나를 고른다.
     *
     * 게임이 총합으로 자동 해금해 주지만 그 총합이 무엇의 합인지가 확인되지 않아, 워크북과
     * 같이 **직접 고르는** 값으로 둔다.
     */
    val deckScoreChoices: List<DeckScoreChoice> = emptyList(),
) {
    /**
     * 중계 인원. 총원이 고정이라 선발·마무리에서 자동으로 정해진다.
     *
     * jsonb에 넣지 않는다. 저장하면 선발 수와 어긋날 여지가 생기고, 무엇보다 읽을 때
     * 생성자에 없는 필드라 역직렬화가 깨진다.
     */
    @get:JsonIgnore
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
 * @param cardGrade 카드 등급(서열). @param cardVariant 변형(FA·WBC). 둘은 다른 축이다.
 * @param position 채점에 쓰는 포지션. 주전과 투수는 [slot]에서 유도하므로 클라이언트 값을 믿지 않고,
 *   후보만 직접 받는다(후보 포수는 C 전용 스킬을 골라야 한다).
 * @param battingOrder 타순. 주전 9명만 가지며 1~9가 중복 없이 채워진다.
 * @param pitcherSlot 투수 슬롯 번호. [slot]의 숫자 부분이며 조건 게이트(`선발1_2` 등)가 이 값을 본다.
 * @param relieverRole 중계 하위 역할. 중계에만 있고 나머지는 null이다. 점수에는 쓰이지 않는다.
 * @param stats 보유 능력치. 키는 stat_weights.csv의 이름만 허용한다.
 * @param statsSlot [stats]를 적을 당시 이 선수가 서 있던 자리. 보유 능력치에는 그 자리의
 *   포지션 훈련이 이미 들어 있으므로, 다른 자리에 세우면 두 자리의 차이만큼 보정한다.
 *   비어 있으면 지금 자리에서 적은 것으로 보고 보정하지 않는다.
 * @param baseStats 카드 고유 능력치. 육성을 하나도 하지 않은 값이며 [stats]와 다르다.
 *   **이 값이 있는 스탯은 성분을 쌓아 최종 능력치를 만든다**(기본 + 훈련 + 특훈 + 초월 +
 *   강화 + 포훈 + 덱 스코어 보상 + 컬렉션 버프). 없으면 [stats]를 최종값으로 그대로 쓴다.
 *   `기본주루수비합155이상` 같은 조건도 이 값을 보므로 폴백 확률 대신 확정값으로 채점된다.
 * @param trainingStats 훈련으로 올린 값. @param specialTrainingStats 특훈(라픽 포함)으로 올린 값.
 * @param year 카드 연도. 스페셜 덱 스코어의 연대 보상이 이 값을 본다.
 */
data class DeckPlayer(
    val slot: String,
    val position: String,
    /** 선수 이름. 비어 있을 수 있고 점수에는 영향이 없다. */
    val playerName: String? = null,
    /** 카드 등급. 상대등급우세 조건이 이 값을 본다. */
    val cardGrade: String,
    /** 카드 변형. 서열에는 영향이 없고 WBC만 스킬 풀을 넓힌다. */
    val cardVariant: String = "NONE",
    val skills: List<DeckSkillSelection>,
    val battingOrder: Int? = null,
    val pitcherSlot: Int? = null,
    val relieverRole: RelieverRole? = null,
    val stats: Map<String, Double> = emptyMap(),
    val statsSlot: String? = null,
    val throwHand: Handedness? = null,
    val batHand: Handedness? = null,
    val baseStats: Map<String, Double> = emptyMap(),
    val trainingStats: Map<String, Double> = emptyMap(),
    val specialTrainingStats: Map<String, Double> = emptyMap(),
    /** 초월 레벨. 0~15이되 카드마다 상한이 다르다. */
    val transcendenceLevel: Int? = null,
    /** 강화 레벨. 1~20이되 블랙 계열은 10에서 멈춘다. */
    val enhancementLevel: Int? = null,
    val year: Int? = null,
)

/**
 * 선수가 가진 스킬 하나와 그 레벨.
 *
 * @param option 워크북 점수표의 옵션 변형을 직접 고른 것. 비우면 선수 상황으로 판정한다.
 *   엑셀에서 가져온 덱은 워크북이 적어 둔 변형을 그대로 들고 온다("그대로 반영").
 */
data class DeckSkillSelection(
    val skillId: String,
    val level: Int,
    val option: String? = null,
)

/**
 * 덱 스코어 보상에서 고른 한 칸.
 *
 * @param decadeYear 연대 보상(스페셜 615·645·680)에서 고른 연대. 그 카드의 연도가
 *   `[decadeYear, decadeYear + 9]` 안이면 보상을 받는다.
 */
data class DeckScoreChoice(
    val ladder: DeckScoreLadder,
    val threshold: Int,
    val side: DeckScoreSide,
    val decadeYear: Int? = null,
)
