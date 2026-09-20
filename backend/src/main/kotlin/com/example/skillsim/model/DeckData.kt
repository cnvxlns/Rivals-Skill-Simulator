package com.example.skillsim.model

/**
 * 덱 계산에 쓰는 정적 데이터. 전부 클래스패스 CSV에서 부팅 때 한 번 읽는다.
 *
 * 스킬 데이터(`score_skills.csv`)와 같은 방침이다 — DB에 넣지 않는다. 원천은 커뮤니티
 * 덱 관리 워크북이고, `tools/deck_workbook.py`가 그 워크북에서 CSV를 뽑는다.
 */

/** 카드 성장 표의 열쇠. 변형이 별도 수치를 갖는다(시그니처와 FA시그니처의 강화가 다르다). */
data class StatGrowthKey(
    val cardGrade: String,
    val cardVariant: String,
    val stat: String,
)

/**
 * 레벨별 누적 능력치.
 *
 * 사다리 길이가 카드마다 다르다. 강화는 블랙 계열만 10에서, 초월은 시그니처·프라임 계열이
 * 9에서 멈춘다. 게임의 실제 상한이라 억지로 채우지 않는다.
 *
 * @param baseLevel 첫 칸이 몇 레벨인가. 초월은 0부터, 강화는 1부터다.
 */
data class StatGrowth(
    val key: StatGrowthKey,
    val baseLevel: Int,
    val values: List<Int>,
) {
    val maxLevel: Int get() = baseLevel + values.size - 1

    /** 레벨이 사다리를 넘으면 마지막 값에서 멈춘다. 모자라면 0이다. */
    fun at(level: Int): Int {
        if (values.isEmpty() || level < baseLevel) return 0
        return values[minOf(level - baseLevel, values.size - 1)]
    }
}

/** 덱 스코어 보상의 두 사다리. 게임 화면의 `팀 덱 스코어` / `스페셜 덱 스코어`다. */
enum class DeckScoreLadder { TEAM, SPECIAL }

/** 임계값마다 둘 중 하나를 고른다. */
enum class DeckScoreSide { LEFT, RIGHT }

/** 보상이 걸리는 조건. 워크북 수식에서 뽑은 여덟 가지가 전부다. */
enum class DeckScoreCondition {
    ALWAYS,
    CARD_LIVE_SEASON,
    CARD_NOT_LIVE_SEASON,
    ORDER_1_2,
    ORDER_3_5,
    ORDER_6_9,
    ENHANCE_GTE_10,
    DECADE,
}

/**
 * 보상을 받는 자리.
 *
 * 워크북은 `SP1`~`SP5`·`RP1`~`RP3`·`CP1`까지만 안다. 우리 덱은 선발 6·중계 7·마무리 2까지
 * 갈 수 있으므로 역할 이름([Group])으로 옮겨 두면 그 자리들도 같은 보상을 받는다.
 * 특정 포지션만 받는 규칙은 [slots]로 적는다(`1B|3B`처럼).
 */
data class DeckScoreTarget(
    val group: Group?,
    val slots: Set<String>,
) {
    enum class Group { BATTER_ALL, PITCHER_ALL, SP, RP, RP_CP, CP }

    companion object {
        fun parse(raw: String): DeckScoreTarget {
            val group = Group.entries.firstOrNull { it.name == raw }
            return if (group != null) {
                DeckScoreTarget(group, emptySet())
            } else {
                DeckScoreTarget(null, raw.split("|").map { it.trim() }.filter { it.isNotEmpty() }.toSet())
            }
        }
    }
}

/** 덱 스코어 보상 한 줄. */
data class DeckScoreReward(
    val ladder: DeckScoreLadder,
    val threshold: Int,
    val side: DeckScoreSide,
    val target: DeckScoreTarget,
    val stat: String,
    val amount: Int,
    val condition: DeckScoreCondition,
)

/**
 * 워크북 스킬 점수표 한 줄.
 *
 * 같은 스킬·레벨이라도 옵션 변형마다 점수가 다르다. [selector]가 선수 상황을 보고 어느
 * 변형인지 정하고, 정할 수 없으면(`MANUAL`) 사용자가 고르거나 엔진 값으로 떨어진다.
 *
 * @param source 워크북의 원래 문자열. 값을 되짚을 때 쓴다.
 */
data class ExcelSkillScore(
    val skillId: String,
    val level: Int,
    val option: String,
    val score: Double,
    val selector: String,
    val source: String,
)

/**
 * 라인업 전체를 올려 주는 스킬.
 *
 * 게임 설명문이 "라인업에 등록된 모든 타자/투수"라고 적은 것들이다. 우리 채점은 지금까지
 * 보유자 한 명에게만 적용하고 있었다. 워크북은 이 부분을 스킬 점수에서 빼 두고 팀 설정
 * 드롭다운으로 따로 받는다 — 그래서 덱에서는 워크북 점수를 쓰고 이 표로 팀 전체를 올린다.
 *
 * @param requiresSlot 보유자가 그 자리에 있어야 효과가 나는 경우(커맨더·포수 리드의 `C`).
 */
data class TeamBuffSkill(
    val skillId: String,
    val scope: Scope,
    val requiresSlot: String?,
    val stat: String,
    val condition: String,
    val values: List<Double>,
    /**
     * CSV에 적힌 사다리 문자열 그대로.
     *
     * 채점할 때 이 효과행을 빼야 한다 — 팀 전체에 더하면서 보유자에게 또 주면 두 번 센다.
     * 같은 스킬·스탯·조건이 자기 효과와 팀 효과로 두 번 나오는 경우가 있어(WBC 에이스)
     * 값 사다리까지 맞춰야 정확히 그 줄만 집어낼 수 있다.
     */
    val rawValues: String = values.joinToString("/"),
) {
    enum class Scope { BATTER, PITCHER }

    /** 레벨이 사다리를 넘으면 마지막 값에서 멈춘다. `ScoreCalculator.valueAt`과 같은 규칙이다. */
    fun at(level: Int): Double {
        if (values.isEmpty()) return 0.0
        return values[minOf(maxOf(level - 1, 0), values.size - 1)]
    }
}
