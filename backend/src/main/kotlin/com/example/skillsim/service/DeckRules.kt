package com.example.skillsim.service

import com.example.skillsim.model.DeckRoster

/**
 * 덱 구성 규칙. 상수와 순수 함수만 두고 예외를 던지지 않는다.
 *
 * [SkillRules]와 같은 결이다. 검증에서 오는 메시지는 [DeckValidator]가 만든다.
 */
internal object DeckRules {

    private const val STARTER_PREFIX = "SP"
    private const val RELIEVER_PREFIX = "RP"
    private const val CLOSER_PREFIX = "CP"

    /** 주전 타자 자리. 순서가 곧 화면 표시 순서다. */
    val LINEUP_SLOTS = listOf("C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")

    /** 후보 타자 자리. */
    val BENCH_SLOTS = (1..5).map { "BENCH$it" }

    val STARTER_COUNT_RANGE = 4..6
    val CLOSER_COUNT_RANGE = 1..2

    /**
     * 중계 인원의 범위. 총원이 12로 고정이라 선발·마무리 범위에서 파생된다.
     *
     * 선발 4~6, 마무리 1~2이므로 중계는 12−6−2=4에서 12−4−1=7 사이다.
     */
    val RELIEVER_COUNT_RANGE = 4..7

    /**
     * 유효한 (선발, 중계, 마무리) 조합 전체. 6가지뿐이다.
     *
     * 셋 중 둘을 정하면 합이 12로 고정이라 나머지는 산술적으로 유일하게 정해진다.
     * 다만 그 값이 범위를 벗어날 수 있어(예: 선발4 + 중계4 → 마무리4) 조합 자체를 검사한다.
     */
    val VALID_PITCHER_COMBOS: List<Triple<Int, Int, Int>> =
        STARTER_COUNT_RANGE.flatMap { starters ->
            CLOSER_COUNT_RANGE.map { closers ->
                Triple(starters, DeckRoster.PITCHER_COUNT - starters - closers, closers)
            }
        }

    /** 선발과 마무리로 정해지는 중계 인원. */
    fun relieverCountFor(starterCount: Int, closerCount: Int): Int =
        DeckRoster.PITCHER_COUNT - starterCount - closerCount

    /** 타순에 쓸 수 있는 값. 주전 9명이 이 집합을 정확히 한 번씩 채운다. */
    val BATTING_ORDERS = 1..9

    /**
     * 투수 슬롯 이름 전체. 선발·마무리를 정하면 중계 정원이 따라 정해지므로
     * 이 목록도 두 값만으로 결정된다.
     */
    fun pitcherSlots(starterCount: Int, closerCount: Int): List<String> {
        val relieverCount = DeckRoster.PITCHER_COUNT - starterCount - closerCount
        return buildList {
            (1..starterCount).forEach { add("$STARTER_PREFIX$it") }
            (1..relieverCount).forEach { add("$RELIEVER_PREFIX$it") }
            (1..closerCount).forEach { add("$CLOSER_PREFIX$it") }
        }
    }

    /**
     * 있을 수 있는 자리 전체.
     *
     * 한 덱이 실제로 쓰는 자리는 26개지만 선발·마무리 수에 따라 어느 투수 자리를 쓰는지가
     * 달라진다. 덱과 무관하게 구단 단위로 저장하는 포지션 훈련은 이 목록을 기준으로 받는다.
     */
    val ALL_SLOTS: List<String> = LINEUP_SLOTS + BENCH_SLOTS +
        (1..STARTER_COUNT_RANGE.last).map { "$STARTER_PREFIX$it" } +
        (1..RELIEVER_COUNT_RANGE.last).map { "$RELIEVER_PREFIX$it" } +
        (1..CLOSER_COUNT_RANGE.last).map { "$CLOSER_PREFIX$it" }

    /** 이 덱에 있어야 할 자리 전체. 26개다. */
    fun expectedSlots(starterCount: Int, closerCount: Int): List<String> =
        LINEUP_SLOTS + BENCH_SLOTS + pitcherSlots(starterCount, closerCount)

    fun isBench(slot: String): Boolean = slot in BENCH_SLOTS

    fun isReliever(slot: String): Boolean = slot.startsWith(RELIEVER_PREFIX)

    fun isPitcher(slot: String): Boolean =
        slot.startsWith(STARTER_PREFIX) || slot.startsWith(RELIEVER_PREFIX) || slot.startsWith(CLOSER_PREFIX)

    fun isLineup(slot: String): Boolean = slot in LINEUP_SLOTS

    /**
     * 자리에서 채점용 포지션을 유도한다. 후보는 유도할 수 없어 null을 돌려주고
     * 호출자가 클라이언트 값을 쓴다.
     *
     * 주전은 자리 이름이 곧 포지션이고, 투수는 접두사가 포지션이다.
     * 이렇게 두면 `slot=SP1, position=CP` 같은 불일치가 애초에 만들어지지 않는다.
     */
    fun positionForSlot(slot: String): String? = when {
        isLineup(slot) -> slot
        isPitcher(slot) -> slot.takeWhile { it.isLetter() }
        else -> null
    }

    /** 투수 슬롯 번호. 조건 게이트(`선발1_2`, `중계3_4_5`)가 이 값을 본다. */
    fun pitcherSlotNumber(slot: String): Int? =
        if (isPitcher(slot)) slot.dropWhile { it.isLetter() }.toIntOrNull() else null

    /** 점수 소계를 나누는 파트. */
    fun partOf(slot: String): DeckPart = when {
        isLineup(slot) -> DeckPart.LINEUP
        isBench(slot) -> DeckPart.BENCH
        slot.startsWith(STARTER_PREFIX) -> DeckPart.ROTATION
        else -> DeckPart.BULLPEN
    }
}

/** 덱 점수를 나눠 보여주는 단위. */
internal enum class DeckPart {
    /** 주전 9명. */
    LINEUP,

    /** 후보 5명. */
    BENCH,

    /** 선발진. */
    ROTATION,

    /** 불펜진. 중계와 마무리를 합친다. */
    BULLPEN,
}
