package com.example.skillsim.service

/**
 * 컬렉션 버프 — 같은 계열 카드를 몇 장 모았느냐에 따라 덱 전체 선수의 능력치가 오른다.
 *
 * 상수와 순수 함수만 둔다. [DeckRules]·[CardRules]와 같은 결이며 예외를 던지지 않는다.
 *
 * 임계값은 누적이다. 넘긴 임계값 하나당 +1이므로, 모먼트 계열 9장이면 2·5·8을 넘겨 +3이다.
 */
internal object CollectionBuff {

    /** 카드 등급을 묶는 컬렉션 계열. */
    enum class Family {
        HOF,
        SIGNATURE,
        MOMENT,
        LIVE,
        SEASON,
    }

    /**
     * 타자·투수를 가리지 않고 모든 선수의 능력치를 올리는 계열의 임계값.
     *
     * 시즌만 역할별로 달라 [SEASON_BATTER_THRESHOLDS]·[SEASON_PITCHER_THRESHOLDS]에 따로 둔다.
     */
    private val ALL_PLAYER_THRESHOLDS: Map<Family, List<Int>> = mapOf(
        Family.HOF to listOf(2, 5, 8),
        Family.SIGNATURE to listOf(2, 5, 8, 11, 14, 17),
        Family.MOMENT to listOf(2, 5, 8, 11, 14),
        Family.LIVE to listOf(2, 4, 8, 11),
    )

    private val SEASON_BATTER_THRESHOLDS = listOf(6, 16)
    private val SEASON_PITCHER_THRESHOLDS = listOf(10, 20)

    /**
     * 등급 → 계열.
     *
     * 시그니처 블랙은 시그니처 계열로 센다. 변형(FA·WBC)은 등급을 바꾸지 않으므로
     * `FA 시그니처`도 여기서는 그냥 시그니처다.
     *
     * 임팩트와 프라임은 어느 계열에도 넣지 않는다. 두 등급에 걸리는 컬렉션 버프가
     * 확인되지 않았고, 근거 없이 계열을 만들면 점수가 조용히 부풀기 때문이다.
     */
    private val FAMILY_BY_GRADE: Map<String, Family> = mapOf(
        "HOF" to Family.HOF,
        "SIGNATURE" to Family.SIGNATURE,
        "SIGNATURE_BLACK" to Family.SIGNATURE,
        "MOMENT" to Family.MOMENT,
        "SUPREME_MOMENT" to Family.MOMENT,
        "LIVE" to Family.LIVE,
        "SEASON" to Family.SEASON,
    )

    /** 슈프림 모먼트 한 장은 모먼트 두 장으로 센다. 그래서 집계는 장수가 아니라 가중치 합이다. */
    private const val SUPREME_MOMENT_WEIGHT = 2

    fun familyOf(grade: String): Family? = FAMILY_BY_GRADE[CardRules.normalizeGrade(grade)]

    fun weightOf(grade: String): Int =
        if (CardRules.normalizeGrade(grade) == "SUPREME_MOMENT") SUPREME_MOMENT_WEIGHT else 1

    /** 임계값을 몇 개나 넘겼는가. 그 수가 곧 능력치 증가치다. */
    private fun stepsReached(count: Int, thresholds: List<Int>): Int =
        thresholds.count { count >= it }

    /**
     * 덱에 담긴 카드 등급 목록에서 계열별 집계와 역할별 증가치를 낸다.
     *
     * @param grades 26명의 카드 등급. 슬롯 순서는 상관없다.
     */
    fun of(grades: List<String>): Result {
        val counts = LinkedHashMap<Family, Int>()
        Family.entries.forEach { counts[it] = 0 }
        grades.forEach { grade ->
            familyOf(grade)?.let { family -> counts[family] = counts.getValue(family) + weightOf(grade) }
        }

        val families = Family.entries.map { family ->
            val count = counts.getValue(family)
            val batter: Int
            val pitcher: Int
            if (family == Family.SEASON) {
                batter = stepsReached(count, SEASON_BATTER_THRESHOLDS)
                pitcher = stepsReached(count, SEASON_PITCHER_THRESHOLDS)
            } else {
                val steps = stepsReached(count, ALL_PLAYER_THRESHOLDS.getValue(family))
                batter = steps
                pitcher = steps
            }
            FamilyCount(family = family, count = count, batterBonus = batter, pitcherBonus = pitcher)
        }

        return Result(
            families = families,
            batterBonus = families.sumOf { it.batterBonus },
            pitcherBonus = families.sumOf { it.pitcherBonus },
        )
    }

    /**
     * @param count 가중치 합. 슈프림 모먼트가 섞이면 실제 장수보다 크다.
     */
    data class FamilyCount(
        val family: Family,
        val count: Int,
        val batterBonus: Int,
        val pitcherBonus: Int,
    )

    /** 계열별 증가치를 모두 더한 결과. 계열끼리도 누적된다. */
    data class Result(
        val families: List<FamilyCount>,
        val batterBonus: Int,
        val pitcherBonus: Int,
    ) {
        fun bonusFor(isPitcher: Boolean): Int = if (isPitcher) pitcherBonus else batterBonus
    }
}
