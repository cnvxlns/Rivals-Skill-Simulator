package com.example.skillsim.service

import com.example.skillsim.enums.Handedness
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ScoreCalculatorTest {

    @Test
    fun `calculates total per skill and per stat with weights and conditions`() {
        val skill = scoreSkill(
            "S_001", "좌투선호",
            effect("파워", "ALWAYS", "1/2/3"),
            effect("정확", "주자있음", "2/4/6"),
            effect("제구", "ALWAYS", "10/10/10"),
            effect("구위", "모드_리그", "100/100/100"),
        )

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 2)),
            mapOf("파워" to 1.10, "정확" to 0.90, "제구" to 0.00, "구위" to 1.20),
            mapOf("주자있음" to 0.40, "모드_리그" to 0.00),
        )

        assertThat(result.total).isEqualTo(3.64)
        // perStat 은 스킬이 올려주는 스탯 절대치(순수 증가량)이므로 weight·조건확률을 제외한 value 만 표시한다.
        assertThat(result.perStat).containsEntry("파워", 2.00)
        assertThat(result.perStat).containsEntry("정확", 4.00)
        assertThat(result.perStat).containsEntry("제구", 10.00)
        assertThat(result.perStat).containsEntry("구위", 100.00)
        assertThat(result.perSkill).hasSize(1)
        assertThat(result.perSkill[0].skillKey).isEqualTo("S_001")
        assertThat(result.perSkill[0].score).isEqualTo(3.64)
        assertThat(result.perSkill[0].perStat).containsEntry("정확", 4.00)
    }

    @Test
    fun `clamps level to available value range`() {
        val skill = scoreSkill("S_002", "우투선호", effect("파워", "ALWAYS", "5/7"))
        val calculator = ScoreCalculator()

        val belowMin = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 0)),
            mapOf("파워" to 1.0),
            emptyMap(),
        )
        val aboveMax = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 9)),
            mapOf("파워" to 1.0),
            emptyMap(),
        )

        assertThat(belowMin.total).isEqualTo(5.00)
        assertThat(aboveMax.total).isEqualTo(7.00)
    }

    @Test
    fun `treats multiple mode conditions as alternatives`() {
        val skill = scoreSkill("G_016", "순위경쟁", effect("파워", "모드_랭킹대전+모드_라이브매치", "3"))

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            mapOf("모드_랭킹대전" to 1.0, "모드_라이브매치" to 0.0),
        )

        assertThat(result.total).isEqualTo(3.00)
    }

    @Test
    fun `applies role based inning condition probabilities`() {
        val skill = scoreSkill("G_018", "후반 집중력", effect("파워", "7회이후", "10"))

        assertThat(totalForRole(skill, "파워", "BATTER")).isEqualTo(2.50)
        assertThat(totalForRole(skill, "파워", "SP")).isEqualTo(1.20)
        assertThat(totalForRole(skill, "파워", "RP")).isEqualTo(7.90)
        assertThat(totalForRole(skill, "파워", "CP")).isEqualTo(10.00)
    }

    @Test
    fun `applies role based inning range and until probabilities`() {
        val starter = ScoreCalculator.conditionProbabilitiesForPosition("SP")
        val reliever = ScoreCalculator.conditionProbabilitiesForPosition("RP")

        assertThat(starter).containsEntry("1_3회", 0.54)
        assertThat(starter).containsEntry("4_6회", 0.34)
        assertThat(starter).containsEntry("7_9회", 0.12)
        assertThat(starter).containsEntry("6회까지", 0.88)
        assertThat(starter).containsEntry("7회까지", 0.94)
        assertThat(reliever).containsEntry("1_3회", 0.00)
        assertThat(reliever).containsEntry("7_9회", 0.79)
    }

    @Test
    fun `applies plate appearance probabilities by batting order group`() {
        val defaultOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER")
        val topOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 1)
        val middleOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4)
        val lowerOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 8)

        // 타순 미지정은 1번타자와 같은 분포를 쓴다(예전에는 중위타선 평균이었다).
        assertThat(defaultOrder["타석1"]).isEqualTo(topOrder["타석1"])
        assertThat(middleOrder["타석2"]).isCloseTo(1.0 / 3.595, within(0.0001))
        assertThat(middleOrder["타석3"]).isCloseTo(0.90 / 3.595, within(0.0001))
        assertThat(middleOrder["타석4_7"]).isCloseTo(0.695 / 3.595, within(0.0001))
        assertThat(topOrder["타석7"]).isCloseTo(0.01 / 3.96, within(0.0001))
        assertThat(lowerOrder["타석7"]).isEqualTo(0.0)
    }

    @Test
    fun `combines position gate and inning until conditions as multiplication`() {
        val skill = scoreSkill("G_055", "퀄리티 스타트", effect("파워", "포지션_SP+6회까지", "10"))

        assertThat(totalForRole(skill, "파워", "SP")).isEqualTo(8.80)
        assertThat(totalForRole(skill, "파워", "BATTER")).isEqualTo(0.00)
    }

    @Test
    fun `calculates pitcher overpace inning tier expected value for starter`() {
        val skill = scoreSkill(
            "G_075", "오버페이스",
            effect("구위", "1_3회", "7"),
            effect("구위", "4_6회", "5"),
            effect("구위", "7_9회", "2"),
        )

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("구위" to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition("SP"),
        )

        assertThat(result.total).isEqualTo(5.72)
    }

    @Test
    fun `calculates batter overpace plate tier expected value for middle order`() {
        val skill = scoreSkill(
            "G_034", "오버페이스",
            effect("파워", "타석1", "8"),
            effect("파워", "타석2", "6"),
            effect("파워", "타석3", "4"),
            effect("파워", "타석4_7", "2"),
        )

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4),
        )

        assertThat(result.total).isEqualTo(5.28)
    }

    @Test
    fun `applies role based nine batter duration probabilities`() {
        val skill = scoreSkill("M_029", "스탠드아웃", effect("구위", "등판후9타자", "10"))

        assertThat(totalForRole(skill, "구위", "BATTER")).isEqualTo(0.00)
        assertThat(totalForRole(skill, "구위", "SP")).isEqualTo(4.50)
        assertThat(totalForRole(skill, "구위", "RP")).isEqualTo(9.50)
        assertThat(totalForRole(skill, "구위", "CP")).isEqualTo(10.00)
    }

    @Test
    fun `exposes role based short batter duration probabilities`() {
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("BATTER"))
            .containsEntry("등판후3타자", 0.00)
            .containsEntry("등판후4타자", 0.00)
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("SP"))
            .containsEntry("등판후3타자", 0.15)
            .containsEntry("등판후4타자", 0.20)
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("RP"))
            .containsEntry("등판후3타자", 0.65)
            .containsEntry("등판후4타자", 0.80)
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("CP"))
            .containsEntry("등판후3타자", 0.90)
            .containsEntry("등판후4타자", 1.00)
    }

    @Test
    fun `applies second plate appearance duration probabilities by batting order`() {
        val skill = scoreSkill("M_032", "스탠드아웃", effect("파워", "두번째타석까지", "10"))
        val calculator = ScoreCalculator()

        fun totalForOrder(battingOrder: Int?) = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition("BATTER", battingOrder),
        ).total

        // 타순 미지정은 1번타자와 같은 값이어야 한다.
        assertThat(totalForOrder(null)).isEqualTo(totalForOrder(1))
        assertThat(totalForOrder(1)).isEqualTo(5.00)
        assertThat(totalForOrder(4)).isEqualTo(5.50)
        assertThat(totalForOrder(8)).isEqualTo(5.80)
    }

    @Test
    fun `applies stat comparison probabilities using documented patience below velocity table`() {
        val skill = scoreSkill("M_041", "파워 피처", effect("파워", "인내<구속", "10"))

        assertThat(totalForRole(skill, "파워", "BATTER")).isEqualTo(8.00)
        assertThat(totalForRole(skill, "파워", "SP")).isEqualTo(2.00)
        assertThat(totalForRole(skill, "파워", "RP")).isEqualTo(0.50)
        assertThat(totalForRole(skill, "파워", "CP")).isEqualTo(1.00)
    }

    @Test
    fun `applies same role based probability to OVR underdog and guts tokens`() {
        assertThat(totalForCondition("BATTER", "OVR열세")).isEqualTo(2.00)
        assertThat(totalForCondition("SP", "OVR열세")).isEqualTo(8.00)
        assertThat(totalForCondition("RP", "OVR열세")).isEqualTo(9.50)
        assertThat(totalForCondition("CP", "OVR열세")).isEqualTo(9.00)
        assertThat(totalForCondition("RP", "패기")).isEqualTo(9.50)
    }

    @Test
    fun `applies inverse of guts probability to patience below velocity tokens`() {
        assertThat(totalForCondition("BATTER", "구속>인내")).isEqualTo(8.00)
        assertThat(totalForCondition("SP", "구속>인내")).isEqualTo(2.00)
        assertThat(totalForCondition("RP", "구속>인내")).isEqualTo(0.50)
        assertThat(totalForCondition("CP", "구속>인내")).isEqualTo(1.00)
    }

    @Test
    fun `applies R19 static condition probabilities`() {
        assertThat(totalForCondition("BATTER", "덱스코어열세")).isEqualTo(5.00)
        assertThat(totalForCondition("BATTER", "홈런3이상")).isEqualTo(0.05)
    }

    @Test
    fun `exposes conditional normal skill probability tokens`() {
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("BATTER"))
            .containsEntry("대타첫타석", 0.0)
            .containsEntry("교체후첫타자", 0.25)
    }

    @Test
    fun `applies R19 stat comparison condition probabilities`() {
        assertThat(totalForCondition("BATTER", "구위>파워")).isEqualTo(3.50)
        assertThat(totalForCondition("BATTER", "선구>제구")).isEqualTo(6.50)
    }

    @Test
    fun `born to hit stat comparison is always applied`() {
        val skill = scoreSkill("M_044", "본 투 히트", effect("파워", "파워정확합>주루수비합", "12"))
        val calculator = ScoreCalculator()
        val probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER")

        fun totalWith(userStats: Map<String, Double>) = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            probabilities,
            userStats,
        ).total

        assertThat(totalWith(mapOf("파워" to 130.0, "정확" to 125.0, "주루" to 100.0, "수비" to 100.0)))
            .isEqualTo(12.00)
        assertThat(totalWith(mapOf("파워" to 100.0, "정확" to 100.0, "주루" to 130.0, "수비" to 125.0)))
            .isEqualTo(12.00)
        assertThat(totalWith(emptyMap())).isEqualTo(12.00)
    }

    @Test
    fun `applies R19 pitch command over discipline probability by role`() {
        assertThat(totalForCondition("BATTER", "제구>선구")).isEqualTo(4.50)
        assertThat(totalForCondition("SP", "제구>선구")).isEqualTo(4.50)
        assertThat(totalForCondition("RP", "제구>선구")).isEqualTo(1.00)
        assertThat(totalForCondition("CP", "제구>선구")).isEqualTo(2.50)
    }

    @Test
    fun `applies opponent grade advantage probability by own card type`() {
        assertThat(totalForCondition("BATTER", "상대등급우세")).isEqualTo(0.50)
        assertThat(totalForCondition("BATTER", "상대등급우세", "MOMENT")).isEqualTo(4.00)
        assertThat(totalForCondition("BATTER", "상대등급우세", "SUPREME_MOMENT")).isEqualTo(3.00)
        assertThat(totalForCondition("BATTER", "상대등급우세", "SIGNATURE")).isEqualTo(2.00)
        assertThat(totalForCondition("BATTER", "상대등급우세", "WBC")).isEqualTo(2.00)
        assertThat(totalForCondition("BATTER", "상대등급우세", "SIGNATURE_BLACK")).isEqualTo(0.50)
        assertThat(totalForCondition("BATTER", "상대등급우세", "WBC_SIGNATURE_BLACK")).isEqualTo(0.50)
        assertThat(totalForCondition("BATTER", "상대등급우세", "HOF")).isEqualTo(0.00)
    }

    @Test
    fun `WBC tier matches its non-WBC counterpart as a rebrand not a different grade`() {
        val table = ScoreCalculator.opponentGradeAdvantageProbabilitiesByCardType

        // 변형(FA·WBC)은 서열을 바꾸지 않으므로 이 표에 등장하지 않는다.
        assertThat(table).doesNotContainKeys("WBC", "WBC_BLACK", "NORMAL", "BLACK")
    }

    @Test
    fun `opponent grade advantage probabilities are non-increasing up the ladder`() {
        val table = ScoreCalculator.opponentGradeAdvantageProbabilitiesByCardType
        // season = live < impact < prime < moment < signature < signature black < hof
        val lowToHigh = CardRules.GRADES_LOW_TO_HIGH

        for (i in 1 until lowToHigh.size) {
            assertThat(table.getValue(lowToHigh[i])).isLessThanOrEqualTo(table.getValue(lowToHigh[i - 1]))
        }
        assertThat(table["HOF"]).isEqualTo(0.00)
    }

    @Test
    fun `exposes R19 tokens in default batter condition probabilities`() {
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("BATTER"))
            .containsEntry("덱스코어열세", 0.5)
            .containsEntry("홈런3이상", 0.005)
            .containsEntry("구위>파워", 0.35)
            .containsEntry("선구>제구", 0.65)
            .containsEntry("제구>선구", 0.45)
            .containsEntry("상대등급우세", 0.05)
    }

    @Test
    fun `applies maestro cumulative average stack by pitcher role`() {
        val skill = scoreSkill("M_042", "마에스트로", effect("구위", "마에스트로누적", "12"))

        assertThat(totalForRole(skill, "구위", "BATTER")).isEqualTo(0.00)
        assertThat(totalForRole(skill, "구위", "SP")).isEqualTo(7.41)
        assertThat(totalForRole(skill, "구위", "RP")).isEqualTo(1.50)
        assertThat(totalForRole(skill, "구위", "CP")).isEqualTo(1.00)
    }

    @Test
    fun `calculates proportional effects from user stats`() {
        val skill = scoreSkill("G_057", "하드 트레이닝", proportionalEffect("구위", "ALWAYS", "0.05", "지구력"))

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("구위" to 1.20),
            emptyMap(),
            mapOf("지구력" to 200.0),
        )

        assertThat(result.total).isEqualTo(12.00)
        assertThat(result.perStat).containsEntry("구위", 10.00)
    }

    @Test
    fun `sums composite base stat for proportional effects`() {
        val skill = scoreSkill(
            "G_068", "핀포인트 컨트롤",
            proportionalEffect("구위", "ALWAYS", "0.02", "변화+제구"),
        )

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("구위" to 1.20),
            emptyMap(),
            mapOf("변화" to 100.0, "제구" to 50.0),
        )

        // 기준값 = 변화(100) + 제구(50) = 150, value = floor(150 x 0.02) = 3
        assertThat(result.perStat).containsEntry("구위", 3.00)
        assertThat(result.total).isEqualTo(3.60)
        val breakdown = result.perSkill[0].breakdown[0]
        assertThat(breakdown.baseStat).isEqualTo("변화+제구")
        assertThat(breakdown.baseValue).isEqualTo(150.0)
        assertThat(breakdown.value).isEqualTo(3.0)
    }

    @Test
    fun `rejects unknown condition tokens instead of falling back to always active`() {
        val skill = scoreSkill("X_001", "Unknown", effect("파워", "미정의조건", "10"))

        assertThatThrownBy {
            ScoreCalculator().calculate(
                listOf(ScoreCalculator.Selection(skill, 1)),
                mapOf("파워" to 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unknown condition")
    }

    @Test
    fun `gates position conditions by requested position`() {
        val skill = scoreSkill(
            "G_048", "Ace Killer",
            effect("정확", "포지션_SP", "10"),
            effect("선구", "포지션_SP+포지션_RP_CP", "10"),
        )
        val calculator = ScoreCalculator()
        val weights = mapOf("정확" to 1.0, "선구" to 1.0)

        fun totalFor(position: String) = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            weights,
            ScoreCalculator.conditionProbabilitiesForPosition(position),
        ).total

        assertThat(totalFor("BATTER")).isEqualTo(0.00)
        assertThat(totalFor("SP")).isEqualTo(20.00)
        assertThat(totalFor("RP")).isEqualTo(10.00)
    }

    @Test
    fun `applies documented default condition probabilities`() {
        val skill = scoreSkill(
            "B_001", "Condition Defaults",
            effect("파워", "초구", "10"),
            effect("정확", "타순1_2", "10"),
            effect("선구", "리드", "10"),
            effect("인내", "리드아님", "10"),
            effect("주루", "스트라이크타격", "10"),
            effect("수비", "2스트라이크", "10"),
            effect("발사각", "발사각조건", "10"),
        )

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf(
                "파워" to 1.0,
                "정확" to 1.0,
                "선구" to 1.0,
                "인내" to 1.0,
                "주루" to 1.0,
                "수비" to 1.0,
                "발사각" to 1.0,
            ),
            ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
        )

        // perStat 은 순수 증가량(value=10)만 표시하므로, 조건확률 반영은 가중 총점(total)으로 검증한다.
        assertThat(result.perStat).containsEntry("파워", 10.00)
        assertThat(result.perStat).containsEntry("정확", 10.00)
        // 타순을 넘기지 않으면 1번타자로 본다. 타순1_2는 평균 0.222가 아니라 1.0으로 걸린다.
        assertThat(result.total).isEqualTo(34.00)
    }

    @Test
    fun `uses batting order gate when batting order is provided`() {
        val probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4)

        assertThat(probabilities).containsEntry("타순1", 0.0)
        assertThat(probabilities).containsEntry("타순1_2", 0.0)
        assertThat(probabilities).containsEntry("타순3_4_5", 1.0)
        assertThat(probabilities).containsEntry("타순4_5", 1.0)
        assertThat(probabilities).containsEntry("타순6_9", 0.0)
    }

    @Test
    fun `gates single batting order tokens`() {
        val third = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 3)

        assertThat(third).containsEntry("타순2", 0.0)
        assertThat(third).containsEntry("타순3", 1.0)

        val second = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 2)

        assertThat(second).containsEntry("타순2", 1.0)
        assertThat(second).containsEntry("타순3", 0.0)
    }

    /**
     * 카드 고유 능력치 임계는 경기 중 확률이 아니라 카드마다 켜짐/꺼짐이다.
     * 기본 능력치가 들어오면 확률을 무시하고 0/1로 확정해야 한다.
     *
     * 실측 앵커 두 장(fmkorea 야구게임판, 2026-09):
     * 클레멘테 75+90=165로 두 절 모두 만족, 루스 147로 둘 다 미달.
     */
    @Test
    fun `base stat thresholds resolve from the card's own stats`() {
        val clemente = ScoreCalculator.conditionProbabilitiesForPosition(
            "RF", baseStats = mapOf("주루" to 75.0, "수비" to 90.0),
        )
        assertThat(clemente).containsEntry("기본주루수비합155이상", 1.0)
        assertThat(clemente).containsEntry("기본주루수비합165이상", 1.0)

        val ruth = ScoreCalculator.conditionProbabilitiesForPosition(
            "RF", baseStats = mapOf("주루" to 57.0, "수비" to 90.0),
        )
        assertThat(ruth).containsEntry("기본주루수비합155이상", 0.0)
        assertThat(ruth).containsEntry("기본주루수비합165이상", 0.0)

        // 155는 넘고 165는 못 넘는 중간 카드.
        val between = ScoreCalculator.conditionProbabilitiesForPosition(
            "CF", baseStats = mapOf("주루" to 80.0, "수비" to 80.0),
        )
        assertThat(between).containsEntry("기본주루수비합155이상", 1.0)
        assertThat(between).containsEntry("기본주루수비합165이상", 0.0)
    }

    /**
     * 기본 능력치가 없으면 표본 비율로 떨어진다. 값 자체는 추정이라 바뀔 수 있지만
     * 165가 155보다 흔할 수는 없다 — 더 센 조건이므로 부분집합이다.
     */
    @Test
    fun `base stat thresholds fall back to a population rate when stats are missing`() {
        val unknown = ScoreCalculator.conditionProbabilitiesForPosition("CF")
        val loose = unknown.getValue("기본주루수비합155이상")
        val strict = unknown.getValue("기본주루수비합165이상")

        assertThat(loose).isBetween(0.0, 1.0)
        assertThat(strict).isBetween(0.0, 1.0)
        assertThat(strict).isLessThanOrEqualTo(loose)
        // 1.0으로 두면 점수표에서 이 스킬이 상한값으로 보인다. 루스조차 155를 못 넘는다.
        assertThat(loose).isLessThan(1.0)

        // 스탯이 일부만 오면 카드를 특정할 수 없으므로 표본 비율을 그대로 쓴다.
        val partial = ScoreCalculator.conditionProbabilitiesForPosition(
            "CF", baseStats = mapOf("주루" to 90.0),
        )
        assertThat(partial).containsEntry("기본주루수비합155이상", loose)
    }

    @Test
    fun `gates second base position`() {
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("2B"))
            .containsEntry("포지션_2B", 1.0)
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("SS"))
            .containsEntry("포지션_2B", 0.0)
    }

    /**
     * 스위치 타자는 좌타 절과 스위치 절을 모두 만족한다. 치퍼(스위치 조건)와
     * 리틀 빅맨(좌타 조건)이 한 카드에서 동시에 걸릴 수 있어야 한다.
     */
    @Test
    fun `gates switch hitter separately from left handed batter`() {
        val switch = ScoreCalculator.conditionProbabilitiesForPosition(
            "2B", batHand = Handedness.SWITCH,
        )
        assertThat(switch).containsEntry("스위치타", 1.0)
        assertThat(switch).containsEntry("좌타", 1.0)

        val left = ScoreCalculator.conditionProbabilitiesForPosition(
            "2B", batHand = Handedness.LEFT,
        )
        assertThat(left).containsEntry("스위치타", 0.0)
        assertThat(left).containsEntry("좌타", 1.0)
    }

    /**
     * 컬렉션 버프는 기준 스탯을 올린다. 다만 floor 때문에 정수 경계를 넘겨야 점수가 움직인다.
     */
    @Test
    fun `stat bonus raises the base stat of proportional effects`() {
        val skill = scoreSkill("G_057", "하드 트레이닝", proportionalEffect("변화", "ALWAYS", "0.06", "지구력"))
        val totalWith = { bonus: Double ->
            ScoreCalculator().calculate(
                listOf(ScoreCalculator.Selection(skill, 1)),
                mapOf("변화" to 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                emptyMap(),
                bonus,
            ).perStat.getValue("변화")
        }

        // 기본 지구력 120 x 0.06 = 7.2 -> 7. +12로는 7.92라 아직 7이다.
        assertThat(totalWith(0.0)).isEqualTo(7.00)
        assertThat(totalWith(12.0)).isEqualTo(7.00)
        // +14면 8.04가 되어 비로소 한 칸 오른다.
        assertThat(totalWith(14.0)).isEqualTo(8.00)
    }

    @Test
    fun `stat bonus applies to each part of a composite base stat`() {
        val skill = scoreSkill("G_001", "호타준족", proportionalEffect("파워", "ALWAYS", "0.03", "주루+수비"))
        val value = { bonus: Double ->
            ScoreCalculator().calculate(
                listOf(ScoreCalculator.Selection(skill, 1)),
                mapOf("파워" to 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                emptyMap(),
                bonus,
            ).perStat.getValue("파워")
        }

        // 주루 120 + 수비 120 = 240 -> floor(7.2) = 7.
        assertThat(value(0.0)).isEqualTo(7.00)
        // 두 능력치가 각각 오르므로 기준값은 +10이 아니라 +20이 된다. floor(7.8) = 7.
        assertThat(value(5.0)).isEqualTo(7.00)
        assertThat(value(10.0)).isEqualTo(7.00)
        // 260 x 0.03 = 7.8, 280 x 0.03 = 8.4.
        assertThat(value(20.0)).isEqualTo(8.00)
    }

    @Test
    fun `stat bonus does not touch deck score base stats`() {
        val skill = scoreSkill("G_038", "결속력", proportionalEffect("파워", "ALWAYS", "0.015", "스페셜덱"))
        val value = { bonus: Double ->
            ScoreCalculator().calculate(
                listOf(ScoreCalculator.Selection(skill, 1)),
                mapOf("파워" to 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                emptyMap(),
                bonus,
            ).perStat.getValue("파워")
        }

        // 덱 스코어는 선수 능력치가 아니다. 버프를 아무리 올려도 기준값 500이 그대로다.
        assertThat(value(0.0)).isEqualTo(7.00)
        assertThat(value(50.0)).isEqualTo(7.00)
    }

    @Test
    fun `floors stat increase before applying stat weight`() {
        val skill = scoreSkill("G_038", "결속력", proportionalEffect("파워", "ALWAYS", "0.015", "스페셜덱"))

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.2),
            emptyMap(),
            mapOf("스페셜덱" to 500.0),
        )

        assertThat(result.total).isEqualTo(8.40)
        assertThat(result.perStat).containsEntry("파워", 7.00)
    }

    @Test
    fun `uses deck score default for proportional deck effects`() {
        val skill = scoreSkill("G_038", "결속력", proportionalEffect("파워", "ALWAYS", "0.01", "스페셜덱"))

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.2),
            emptyMap(),
            emptyMap(),
        )

        assertThat(result.total).isEqualTo(6.00)
        assertThat(result.perStat).containsEntry("파워", 5.00)
    }

    @Test
    fun `exposes effect level breakdown for formula display`() {
        val skill = scoreSkill("G_038", "결속력", proportionalEffect("파워", "리드", "0.01", "스페셜덱"))

        val result = ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.2),
            ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
            mapOf("스페셜덱" to 500.0),
        )

        assertThat(result.perSkill[0].breakdown).hasSize(1)
        val breakdown = result.perSkill[0].breakdown[0]
        assertThat(breakdown.stat).isEqualTo("파워")
        assertThat(breakdown.condition).isEqualTo("리드")
        assertThat(breakdown.baseStat).isEqualTo("스페셜덱")
        assertThat(breakdown.baseValue).isEqualTo(500.0)
        assertThat(breakdown.value).isEqualTo(5.0)
        assertThat(breakdown.weight).isEqualTo(1.2)
        assertThat(breakdown.conditionProbability).isEqualTo(0.37)
        assertThat(breakdown.subtotal).isEqualTo(2.22)
    }

    @Test
    fun `verifies slot gate resolver values`() {
        val sp1 = ScoreCalculator.conditionProbabilitiesForPosition("SP", pitcherSlot = 1)
        assertThat(sp1).containsEntry("선발1", 1.0)
        assertThat(sp1).containsEntry("선발1_2", 1.0)
        assertThat(sp1).containsEntry("선발3_4", 0.0)
        assertThat(sp1).containsEntry("선발3_4_5", 0.0)
        assertThat(sp1).containsEntry("선발4_5", 0.0)
        assertThat(sp1).containsEntry("중계3_4_5", 0.0)

        val sp4 = ScoreCalculator.conditionProbabilitiesForPosition("SP", pitcherSlot = 4)
        assertThat(sp4).containsEntry("선발1", 0.0)
        assertThat(sp4).containsEntry("선발1_2", 0.0)
        assertThat(sp4).containsEntry("선발3_4", 1.0)
        assertThat(sp4).containsEntry("선발3_4_5", 1.0)
        assertThat(sp4).containsEntry("선발4_5", 1.0)

        val rp4 = ScoreCalculator.conditionProbabilitiesForPosition("RP", pitcherSlot = 4)
        assertThat(rp4).containsEntry("선발3_4_5", 0.0)
        assertThat(rp4).containsEntry("중계3_4_5", 1.0)

        val spNull = ScoreCalculator.conditionProbabilitiesForPosition("SP")
        assertThat(spNull).containsEntry("선발1", 0.0)
    }

    @Test
    fun `verifies or group logic for pitcher slots`() {
        val skill = scoreSkill("G_052", "라이징 스타", effect("파워", "선발3_4_5+중계3_4_5", "10"))
        val calculator = ScoreCalculator()

        fun totalFor(position: String, slot: Int) = calculator.calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition(position, pitcherSlot = slot),
        ).total

        assertThat(totalFor("SP", 4)).isEqualTo(10.00)
        assertThat(totalFor("RP", 4)).isEqualTo(10.00)
        assertThat(totalFor("SP", 1)).isEqualTo(0.00)
    }

    @Test
    fun `좌완 전용 조건은 우완 투수에게 발동하지 않는다`() {
        // HOF_040 빅 유닛의 "좌완 선발로 등판 시" 절. 우완 SP에게 적용되면 안 된다.
        val rightHanded = ScoreCalculator.conditionProbabilitiesForPosition(
            "SP", cardType = "HOF", throwHand = Handedness.RIGHT, batHand = Handedness.RIGHT,
        )
        assertThat(ScoreCalculator().conditionProbability("좌완+포지션_SP", rightHanded)).isEqualTo(0.0)

        val leftHanded = ScoreCalculator.conditionProbabilitiesForPosition(
            "SP", cardType = "HOF", throwHand = Handedness.LEFT, batHand = Handedness.RIGHT,
        )
        assertThat(ScoreCalculator().conditionProbability("좌완+포지션_SP", leftHanded)).isEqualTo(1.0)
    }

    @Test
    fun `투타 방향이 없으면 우완 우타로 간주한다`() {
        val defaults = ScoreCalculator.conditionProbabilitiesForPosition("SP", cardType = "HOF")

        assertThat(defaults["좌완"]).isEqualTo(0.0)
        assertThat(defaults["우완"]).isEqualTo(1.0)
        assertThat(defaults["우타"]).isEqualTo(1.0)
    }

    @Test
    fun `스위치 타자는 좌타와 우타 조건을 모두 만족한다`() {
        val switchHitter = ScoreCalculator.conditionProbabilitiesForPosition(
            "DH", cardType = "HOF", throwHand = Handedness.RIGHT, batHand = Handedness.SWITCH,
        )

        assertThat(switchHitter["좌타"]).isEqualTo(1.0)
        assertThat(switchHitter["우타"]).isEqualTo(1.0)
        assertThat(switchHitter["스위치타"]).isEqualTo(1.0)
    }

    private fun scoreSkill(skillKey: String, name: String, vararg effects: ScoreEffect): ScoreSkill {
        val skill = ScoreSkill(
            skillKey = skillKey,
            cardType = "NORMAL",
            position = "BATTER",
            name = name,
            description = name,
            effects = effects.toMutableList(),
        )
        skill.effects.forEach { it.skill = skill }
        return skill
    }

    private fun effect(stat: String, condition: String, values: String) =
        ScoreEffect(stat = stat, condition = condition, values = values)

    private fun proportionalEffect(stat: String, condition: String, values: String, baseStat: String) =
        ScoreEffect(stat = stat, condition = condition, values = values, baseStat = baseStat)

    /** 같은 스킬을 보직만 바꿔 채점한다. 보직별 확률표 검증에 반복해서 쓰인다. */
    private fun totalForRole(skill: ScoreSkill, stat: String, role: String): Double =
        ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf(stat to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition(role),
        ).total

    private fun totalForCondition(position: String, condition: String, cardType: String? = null): Double {
        val skill = scoreSkill("G_007", "패기", effect("파워", condition, "10"))

        return ScoreCalculator().calculate(
            listOf(ScoreCalculator.Selection(skill, 1)),
            mapOf("파워" to 1.0),
            ScoreCalculator.conditionProbabilitiesForPosition(position, cardType = cardType),
        ).total
    }
}
