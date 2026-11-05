package com.example.skillsim.service

/**
 * 조건 하나를 발동 또는 미발동으로 못박은 확률표를 만든다.
 *
 * 점수표는 조건마다 발동 확률을 곱한 기댓값으로 채점한다. 여기서는 그 확률 하나를 1이나 0으로
 * 고정해 "그 조건이 걸렸을 때 / 안 걸렸을 때" 순위를 따로 보게 한다.
 *
 * 토큰 하나만 바꾸면 모순이 생기는 경우가 있다. `주자있음`을 1로 두면서 `주자없음`을 0.6으로
 * 남기거나, `7회이후`를 1로 두면서 `6회까지`를 0.58로 남기는 식이다. 그래서 같은 사건 공간을
 * 나누는 토큰은 [Partition]으로 묶어 조건부확률로 함께 고친다. 어느 묶음에도 없는 토큰은
 * 다른 조건과 독립이라 보고 자기 값만 바꾼다.
 */
internal object ConditionScenario {

    private const val RUNNERS_ON = "주자있음"
    private const val RUNNERS_OFF = "주자없음"

    /** `주자있음`의 부분 사건. 주자가 없으면 모두 거짓이다. */
    private val RUNNER_DETAILS = listOf("주자1명", "주자2명이상", "주자2루이상", "주자3루")

    /** 주자 수의 기댓값(`주자당`). 확률이 아니라서 주자가 있다고 정해져도 1에서 자르지 않는다. */
    private val RUNNER_COUNTS = listOf("주자당")

    /** 서로 반대 사건인 쌍. 앞쪽 값으로 뒤쪽을 정한다(`1 - p`). */
    private val COMPLEMENT_PAIRS = listOf(
        "좌투상대" to "우투상대",
        "좌타상대" to "우타상대",
        "직구상대" to "변화구상대",
        "속구선택" to "변화구선택",
        "OVR열세" to "OVR우세",
    )

    /** 이름만 다르고 같은 사건인 토큰. [StatComparisonResolver]가 같은 값을 넣는다. */
    private val ALIASES = mapOf(
        "OVR열세" to listOf("패기"),
        "인내<구속" to listOf("구속>인내"),
    )

    /**
     * 사건 공간을 칸으로 나눈 것. 토큰은 칸의 합집합이다.
     *
     * 한 토큰을 참(또는 거짓)으로 못박으면 남는 칸만으로 다시 나눠 묶음 안 모든 토큰의
     * 조건부확률을 구한다.
     */
    private class Partition(val cells: DoubleArray, val tokens: Map<String, Set<Int>>) {
        fun condition(probabilities: MutableMap<String, Double>, token: String, active: Boolean) {
            val cellsOfToken = tokens.getValue(token)
            val given = cells.indices.filter { (it in cellsOfToken) == active }.toSet()
            val mass = given.sumOf { cells[it] }
            if (mass <= 0.0) {
                return
            }
            for ((other, otherCells) in tokens) {
                probabilities[other] = otherCells.intersect(given).sumOf { cells[it] } / mass
            }
        }
    }

    /**
     * [probabilities]에서 [token]을 [active]로 못박는다. 제자리에서 고친다.
     *
     * @param role [SkillRules.roleForPosition]의 역할. 이닝 분포를 고르는 데 쓴다.
     * @param battingOrder 타석 분포를 고르는 데 쓴다. 없으면 기본 타순이다.
     */
    fun apply(
        probabilities: MutableMap<String, Double>,
        token: String,
        active: Boolean,
        role: String,
        battingOrder: Int?,
    ) {
        // 칸은 못박기 전의 확률로 만들어야 한다.
        val partitions = partitions(probabilities, role, battingOrder)
        val runnersOnBefore = probabilities[RUNNERS_ON] ?: 0.0

        probabilities[token] = if (active) 1.0 else 0.0
        partitions.filter { token in it.tokens }.forEach { it.condition(probabilities, token, active) }
        applyRunnerDetails(probabilities, token, active, runnersOnBefore)
    }

    /**
     * 주자 세부 조건. 칸으로 나누기엔 원래 확률끼리 맞지 않아(1명 0.25 + 2명이상 0.25 < 있음 0.4가
     * 아니다) 부모-자식 관계로만 다룬다. 주자가 있다고 정해지면 자식을 `p / P(주자있음)`으로
     * 올리고, 없다고 정해지면 0으로 내린다. 주자 수 기댓값([RUNNER_COUNTS])도 같이 따라온다.
     */
    private fun applyRunnerDetails(
        probabilities: MutableMap<String, Double>,
        token: String,
        active: Boolean,
        runnersOnBefore: Double,
    ) {
        val runnersOn = when {
            token == RUNNERS_ON -> active
            token == RUNNERS_OFF -> !active
            token in RUNNER_DETAILS + RUNNER_COUNTS && active -> true
            else -> return
        }
        probabilities[RUNNERS_ON] = if (runnersOn) 1.0 else 0.0
        probabilities[RUNNERS_OFF] = if (runnersOn) 0.0 else 1.0
        for (detail in RUNNER_DETAILS + RUNNER_COUNTS) {
            if (detail == token) {
                continue
            }
            val before = probabilities[detail] ?: continue
            val given = if (runnersOnBefore > 0.0) before / runnersOnBefore else before
            probabilities[detail] = when {
                !runnersOn -> 0.0
                detail in RUNNER_COUNTS -> given
                else -> minOf(1.0, given)
            }
        }
    }

    private fun partitions(
        probabilities: Map<String, Double>,
        role: String,
        battingOrder: Int?,
    ): List<Partition> = buildList {
        gameStatePartition(probabilities)?.let(::add)
        previousBatterPartition(probabilities)?.let(::add)
        previousPlatePartition(probabilities)?.let(::add)
        val outTokens = (0..3).map { "처리아웃$it" }
        if (outTokens.all { it in probabilities }) {
            add(Partition(
                outTokens.map { probabilities.getValue(it) }.toDoubleArray(),
                outTokens.mapIndexed { index, token -> token to setOf(index) }.toMap(),
            ))
        }
        for ((left, right) in COMPLEMENT_PAIRS) {
            val p = probabilities[left] ?: continue
            val tokens = mutableMapOf(left to setOf(0), right to setOf(1))
            ALIASES[left].orEmpty().forEach { tokens[it] = setOf(0) }
            add(Partition(doubleArrayOf(p, 1.0 - p), tokens))
        }
        for ((token, aliases) in ALIASES) {
            if (COMPLEMENT_PAIRS.any { it.first == token }) {
                continue
            }
            val p = probabilities[token] ?: continue
            add(Partition(doubleArrayOf(p, 1.0 - p), (listOf(token) + aliases).associateWith { setOf(0) }))
        }
        add(inningPartition(role))
        add(plateAppearancePartition(battingOrder))
    }

    /** 리드 / 동점 / 열세 세 칸. 경기 상황 토큰 넷은 이 칸의 합집합이다. */
    private fun gameStatePartition(probabilities: Map<String, Double>): Partition? {
        val lead = probabilities["리드"] ?: return null
        val leadOrTie = probabilities["비김또는리드"] ?: return null
        return Partition(
            doubleArrayOf(lead, leadOrTie - lead, 1.0 - leadOrTie),
            mapOf(
                "리드" to setOf(0),
                "리드아님" to setOf(1, 2),
                "비김또는리드" to setOf(0, 1),
                "비김또는열세" to setOf(1, 2),
                "열세" to setOf(2),
            ),
        )
    }

    /**
     * 직전 타자의 결과 다섯 칸: 홈런, 그 밖의 출루, 삼진, 그 밖의 아웃, 직전 타자 없음(등판 첫 타자).
     * 홈런은 출루의, 삼진은 아웃의 일부다.
     */
    private fun previousBatterPartition(probabilities: Map<String, Double>): Partition? {
        val onBase = probabilities["직전타자출루"] ?: return null
        val out = probabilities["직전타자아웃"] ?: return null
        val homeRun = probabilities["직전타자홈런"] ?: return null
        val strikeout = probabilities["직전타자삼진"] ?: return null
        return Partition(
            // 타자는 직전 타자가 늘 있어 마지막 칸이 0이다. 뺄셈 오차로 음수가 되지 않게 막는다.
            doubleArrayOf(homeRun, onBase - homeRun, strikeout, out - strikeout, maxOf(0.0, 1.0 - onBase - out)),
            mapOf(
                "직전타자출루" to setOf(0, 1),
                "직전타자홈런" to setOf(0),
                "직전타자아웃" to setOf(2, 3),
                "직전타자삼진" to setOf(2),
            ),
        )
    }

    /**
     * 보유자 본인의 직전 타석 세 칸: 아웃, 홈런 아닌 출루, 나머지(홈런이거나 첫 타석이라 직전 타석이 없음).
     * 출루 실패는 홈런 실패의 일부다.
     */
    private fun previousPlatePartition(probabilities: Map<String, Double>): Partition? {
        val notOnBase = probabilities["직전타석출루실패"] ?: return null
        val noHomeRun = probabilities["직전타석홈런실패"] ?: return null
        return Partition(
            doubleArrayOf(notOnBase, noHomeRun - notOnBase, 1.0 - noHomeRun),
            mapOf(
                "직전타석출루실패" to setOf(0),
                "직전타석홈런실패" to setOf(0, 1),
            ),
        )
    }

    /** 1~9회 아홉 칸. 칸 값은 역할별 회별 노출 비중이다. */
    private fun inningPartition(role: String): Partition {
        val weights = ScoreCalculator.inningWeightsByRole[role]
            ?: ScoreCalculator.inningWeightsByRole.getValue("BATTER")
        val innings = weights.size
        val tokens = mutableMapOf<String, Set<Int>>()
        for (start in 1..innings) {
            val fromStart = (start - 1 until innings).toSet()
            tokens["${start}회이후"] = fromStart
            tokens["${start}회"] = fromStart
            tokens["${start}회까지"] = (0 until start).toSet()
            for (end in start..innings) {
                tokens["${start}_${end}회"] = (start - 1 until end).toSet()
            }
        }
        return Partition(weights, tokens)
    }

    /** 1~7번째 타석 일곱 칸. 칸 값은 타순 그룹의 타석 도달 확률이다. */
    private fun plateAppearancePartition(battingOrder: Int?): Partition {
        val reach = plateAppearanceReach(battingOrder)
        val tokens = mutableMapOf<String, Set<Int>>()
        for (start in 1..reach.size) {
            tokens["타석$start"] = setOf(start - 1)
            for (end in start..reach.size) {
                tokens["타석${start}_$end"] = (start - 1 until end).toSet()
            }
        }
        return Partition(reach.copyOf(), tokens)
    }
}
