package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.enums.TicketType

/**
 * 스킬 변경권을 썼을 때 어떤 티어·레벨이 나오는가.
 *
 * 값은 커밋 `6ef8a77`에서 걷어냈던 `ProbabilityTable`·`HofProbabilityTable`을 그대로 되살린 것이다.
 * 그때 지운 이유는 "화면이 없어져서"였지 수치가 틀려서가 아니었다.
 *
 * 확률은 상수로 둔다. 이 저장소가 CSV를 쓰는 건 스킬 238개·효과 1,429개처럼 큰 데이터라서지,
 * 여기는 숫자 50개쯤이라 로더를 얹으면 드리프트 위험만 는다. [CardRules]·[SkillRules]와 같은 결이다.
 *
 * 가중치의 합은 티켓·등급·슬롯 조합마다 100이다. [RollTablesTest]가 이를 검사한다.
 */
internal object RollTables {

    /** 티어 안에서 레벨이 갈리는 비율. 어느 표든 D 40 : C 30 : B 20 : A 7 : S 3이다. */
    private val LEVEL_SHARE = listOf(0.40, 0.30, 0.20, 0.07, 0.03)

    private val LEVELS = listOf(Level.D, Level.C, Level.B, Level.A, Level.S)

    /** 티어 합계를 레벨별로 쪼갠다. 옛 `gradeRow(...)`가 손으로 적어 두던 값을 계산으로 바꾼다. */
    private fun row(tierTotal: Double): Map<Level, Double> =
        LEVELS.zip(LEVEL_SHARE.map { it * tierTotal }).toMap()

    /**
     * 모먼트 카드 + 최고급의 첫 슬롯.
     *
     * 모먼트 전용이 안 나오면 **골드만** 나온다. 아이언·브론즈·실버는 여기 없다.
     */
    fun momentSlotOneTable(grade: String): Map<SkillTier, Map<Level, Double>> {
        val momentShare = momentSlotOneChance(grade) * 100.0
        return mapOf(
            SkillTier.MOMENT to mapOf(Level.S to momentShare),
            SkillTier.GOLD to row(100.0 - momentShare),
        )
    }

    /**
     * 일반·고급 스킬 변경권이 함께 쓰는 표.
     *
     * 둘이 같은 표를 쓰는 것은 복원한 옛 코드(`ProbabilityTable.fromTicket`)가 그랬기 때문이다.
     * 평범한 카드에서 두 티켓의 차이는 티어 확률이 아니라 "결과를 무를 수 있느냐"뿐이다.
     * HOF·블랙·WBC 카드에서는 아래 표들이 티켓별로 갈린다.
     */
    private val NORMAL_PREMIUM: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.IRON to row(35.0),
        SkillTier.BRONZE to row(30.0),
        SkillTier.SILVER to row(20.0),
        SkillTier.GOLD to row(15.0),
    )

    /** 최고급. 아이언이 아예 빠지고 실버·골드가 두꺼워진다. */
    private val SUPREME: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.BRONZE to row(35.0),
        SkillTier.SILVER to row(40.0),
        SkillTier.GOLD to row(25.0),
    )

    /** 시그니처 블랙에 최고급을 썼을 때, 블랙이 걸리지 않은 나머지 슬롯. */
    private val SIGNATURE_BLACK_SUPREME_OTHER: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.BRONZE to row(20.0),
        SkillTier.SILVER to row(30.0),
        SkillTier.GOLD to row(50.0),
    )

    /** HOF 카드 + 일반 변경권. HOF 티어가 0.1%뿐이다. */
    private val HOF_SKILL_CHANGE: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.IRON to row(34.965),
        SkillTier.BRONZE to row(29.97),
        SkillTier.SILVER to row(19.98),
        SkillTier.GOLD to row(14.985),
        SkillTier.HOF to row(0.1),
    )

    /** HOF 카드 + 고급 변경권. HOF 티어가 1.5%로 열다섯 배가 된다. 여기가 두 티켓이 갈리는 지점이다. */
    private val HOF_PREMIUM: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.IRON to row(34.475),
        SkillTier.BRONZE to row(29.55),
        SkillTier.SILVER to row(19.70),
        SkillTier.GOLD to row(14.775),
        SkillTier.HOF to row(1.5),
    )

    /** HOF 카드 + 최고급 변경권의 첫 슬롯. 골드 아니면 HOF다. */
    private val HOF_SUPREME_SLOT_ONE: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.GOLD to row(90.0),
        SkillTier.HOF to row(10.0),
    )

    /**
     * HOF 카드 + 최고급 변경권의 나머지 슬롯. 아이언이 빠진다.
     *
     * HOF가 가져가는 10%를 뺀 90%를 [SUPREME]의 35 : 40 : 25 비율로 나눈 값이다.
     */
    private val HOF_SUPREME_OTHER: Map<SkillTier, Map<Level, Double>> = mapOf(
        SkillTier.BRONZE to row(31.5),
        SkillTier.SILVER to row(36.0),
        SkillTier.GOLD to row(22.5),
        SkillTier.HOF to row(10.0),
    )

    /** 블랙 티어가 걸렸을 때의 레벨 분포. 티어 안 비율은 같지만 합이 100이라 따로 둔다. */
    val BLACK_LEVEL_WEIGHTS: Map<Level, Double> = row(100.0)

    /** 모먼트 전용 스킬은 항상 S다([SkillRules.gradeLadder]의 MOMENT 사다리가 S 하나뿐이다). */
    val MOMENT_LEVEL_WEIGHTS: Map<Level, Double> = mapOf(Level.S to 100.0)

    /** 고급 변경권이 블랙을 미리 한 칸 잡아 둘 확률. 최고급은 반드시 잡는다. */
    fun blackPreselectChance(ticket: TicketType): Double = when (ticket) {
        TicketType.SKILL_CHANGE -> 0.0
        TicketType.PREMIUM_SKILL_CHANGE -> 0.20
        TicketType.SUPREME_SKILL_CHANGE -> 1.0
    }

    /** WBC 변형 카드에서 한 슬롯이 WBC 전용 스킬로 나올 확률. */
    fun wbcSlotChance(ticket: TicketType): Double = when (ticket) {
        TicketType.SKILL_CHANGE -> 0.0
        TicketType.PREMIUM_SKILL_CHANGE -> 0.005
        TicketType.SUPREME_SKILL_CHANGE -> 0.10
    }

    /**
     * 모먼트 카드에 최고급을 썼을 때 **첫 슬롯**이 모먼트 전용으로 나올 확률.
     *
     * 슈프림 모먼트 30%는 공식 확률 페이지(`SUPREME M SUPERIOR SKILL CHANGE`)와 v4.04.00
     * 공지에 적힌 값이다. 일반 모먼트는 공지가 계속 "일정 확률로"라고만 쓰고 수치를 밝힌 적이
     * 없어, 걷어냈던 스펙 문서(`docs/skillsim_logics.json`)에 남아 있던 6%를 그대로 쓴다.
     * **둘째·셋째 슬롯에는 모먼트 전용이 나오지 않는다.**
     */
    fun momentSlotOneChance(grade: String): Double =
        if (grade == "SUPREME_MOMENT") 0.30 else 0.06

    /**
     * 이 카드·티켓·슬롯에서 쓸 티어 표.
     *
     * @param slotIndex 0부터 센다. 최고급은 첫 슬롯만 규칙이 다르다.
     */
    fun tierTable(family: RollFamily, ticket: TicketType, slotIndex: Int): Map<SkillTier, Map<Level, Double>> =
        when {
            family == RollFamily.HOF -> when (ticket) {
                TicketType.SKILL_CHANGE -> HOF_SKILL_CHANGE
                TicketType.PREMIUM_SKILL_CHANGE -> HOF_PREMIUM
                TicketType.SUPREME_SKILL_CHANGE ->
                    if (slotIndex == 0) HOF_SUPREME_SLOT_ONE else HOF_SUPREME_OTHER
            }

            family == RollFamily.BLACK && ticket == TicketType.SUPREME_SKILL_CHANGE ->
                SIGNATURE_BLACK_SUPREME_OTHER

            ticket == TicketType.SUPREME_SKILL_CHANGE -> SUPREME
            else -> NORMAL_PREMIUM
        }

    /** 표 전체. 합계 검사에 쓴다. */
    internal val ALL_TIER_TABLES: Map<String, Map<SkillTier, Map<Level, Double>>> = mapOf(
        "NORMAL_PREMIUM" to NORMAL_PREMIUM,
        "SUPREME" to SUPREME,
        "SIGNATURE_BLACK_SUPREME_OTHER" to SIGNATURE_BLACK_SUPREME_OTHER,
        "HOF_SKILL_CHANGE" to HOF_SKILL_CHANGE,
        "HOF_PREMIUM" to HOF_PREMIUM,
        "HOF_SUPREME_SLOT_ONE" to HOF_SUPREME_SLOT_ONE,
        "HOF_SUPREME_OTHER" to HOF_SUPREME_OTHER,
    )
}

/**
 * 롤 규칙이 갈리는 카드 묶음.
 *
 * 옛 `CardType`(등급·변형·풀이 뒤섞인 축)을 지금의 등급 + 변형으로 다시 그린 것이다.
 * 옛 축을 되살리면 `1e10e9e`에서 갈라 놓은 것을 도로 무너뜨리게 된다.
 */
internal enum class RollFamily {
    /** 시즌·라이브·임팩트·프라임·시그니처. 기본 티어만 나온다. */
    NORMAL,

    /** WBC 변형 시그니처. */
    WBC,

    /** 시그니처 블랙. 슬롯이 4개다. */
    BLACK,

    /** WBC 변형 시그니처 블랙. */
    WBC_BLACK,

    /** 모먼트·슈프림 모먼트. */
    MOMENT,

    /** HOF. */
    HOF,
    ;

    companion object {
        fun of(grade: String?, variant: String?): RollFamily {
            // 예전 단일 이름(WBC, WBC_BLACK …)으로 들어오면 변형이 그 안에 들어 있다.
            val legacy = CardRules.fromLegacyCardType(grade)
            val normalizedGrade = legacy?.first ?: CardRules.normalizeGrade(grade)
            val normalizedVariant = legacy?.second
                ?.takeIf { it != CardRules.VARIANT_NONE }
                ?: CardRules.normalizeVariant(variant)
            val isWbc = normalizedVariant == CardRules.VARIANT_WBC
            return when {
                normalizedGrade == "HOF" -> HOF
                normalizedGrade == "SIGNATURE_BLACK" -> if (isWbc) WBC_BLACK else BLACK
                normalizedGrade == "MOMENT" || normalizedGrade == "SUPREME_MOMENT" -> MOMENT
                isWbc -> WBC
                else -> NORMAL
            }
        }
    }
}
