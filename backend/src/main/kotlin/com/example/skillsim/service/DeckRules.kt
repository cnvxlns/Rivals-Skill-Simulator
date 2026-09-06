package com.example.skillsim.service

import com.example.skillsim.model.DeckRoster

/**
 * 덱 구성 규칙. 상수와 순수 함수만 두고 예외를 던지지 않는다.
 *
 * [SkillRules]와 같은 결이다. 검증에서 오는 메시지는 [DeckValidator]가 만든다.
 */
internal object DeckRules {

    /** 주전 타자 자리. 순서가 곧 화면 표시 순서다. */
    val LINEUP_SLOTS = listOf("C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")

    /** 후보 타자 자리. */
    val BENCH_SLOTS = (1..5).map { "BENCH$it" }

    val STARTER_COUNT_RANGE = 4..6
    val CLOSER_COUNT_RANGE = 1..2

    /** 타순에 쓸 수 있는 값. 주전 9명이 이 집합을 정확히 한 번씩 채운다. */
    val BATTING_ORDERS = 1..9

    private const val STARTER_PREFIX = "SP"
    private const val RELIEVER_PREFIX = "RP"
    private const val CLOSER_PREFIX = "CP"

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
