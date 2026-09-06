package com.example.skillsim.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CardRulesTest {

    /**
     * 등급+변형 분해가 예전 단일 cardType의 동작을 그대로 재현하는지 확인한다.
     *
     * 기대값은 리팩터링 이전 구현에서 그대로 옮겨 적은 것이다. 이 표가 통과한다는 것은
     * 축을 둘로 나눈 것이 동작을 바꾸지 않았다는 뜻이다.
     */
    @Test
    fun `예전 카드 타입의 스킬 풀과 슬롯 수를 그대로 재현한다`() {
        data class Expected(val grade: String, val variant: String, val pools: List<String>, val slots: Int)

        val cases = mapOf(
            "NORMAL" to Expected("SIGNATURE", "NONE", listOf("NORMAL"), 3),
            "SIGNATURE" to Expected("SIGNATURE", "NONE", listOf("NORMAL"), 3),
            "BLACK" to Expected("SIGNATURE_BLACK", "NONE", listOf("NORMAL", "BLACK"), 4),
            "SIGNATURE_BLACK" to Expected("SIGNATURE_BLACK", "NONE", listOf("NORMAL", "BLACK"), 4),
            "WBC" to Expected("SIGNATURE", "WBC", listOf("NORMAL", "WBC"), 3),
            "WBC_BLACK" to Expected("SIGNATURE_BLACK", "WBC", listOf("NORMAL", "BLACK", "WBC"), 4),
            "WBC_SIGNATURE_BLACK" to Expected("SIGNATURE_BLACK", "WBC", listOf("NORMAL", "BLACK", "WBC"), 4),
            "MOMENT" to Expected("MOMENT", "NONE", listOf("NORMAL", "MOMENT"), 3),
            "SUPREME_MOMENT" to Expected("SUPREME_MOMENT", "NONE", listOf("NORMAL", "MOMENT"), 3),
            "HOF" to Expected("HOF", "NONE", listOf("NORMAL", "HOF"), 3),
            "LIVE" to Expected("LIVE", "NONE", listOf("NORMAL"), 3),
            "SEASON" to Expected("SEASON", "NONE", listOf("NORMAL"), 3),
        )

        for ((legacy, expected) in cases) {
            val (grade, variant) = CardRules.fromLegacyCardType(legacy)!!
            assertThat(grade).`as`("%s 등급", legacy).isEqualTo(expected.grade)
            assertThat(variant).`as`("%s 변형", legacy).isEqualTo(expected.variant)
            assertThat(CardRules.skillPools(grade, variant))
                .`as`("%s 스킬 풀", legacy)
                .containsExactlyInAnyOrderElementsOf(expected.pools)
            assertThat(CardRules.slotCount(grade)).`as`("%s 슬롯", legacy).isEqualTo(expected.slots)
        }
    }

    @Test
    fun `서열은 시즌 라이브가 최하위이고 HOF가 최상위다`() {
        val order = CardRules.GRADES_LOW_TO_HIGH

        assertThat(order.first()).isEqualTo("SEASON")
        assertThat(order.last()).isEqualTo("HOF")
        // season=live < impact < prime < moment < signature < signature black < hof
        assertThat(order.indexOf("LIVE")).isLessThan(order.indexOf("IMPACT"))
        assertThat(order.indexOf("IMPACT")).isLessThan(order.indexOf("PRIME"))
        assertThat(order.indexOf("PRIME")).isLessThan(order.indexOf("MOMENT"))
        assertThat(order.indexOf("MOMENT")).isLessThan(order.indexOf("SIGNATURE"))
        assertThat(order.indexOf("SIGNATURE")).isLessThan(order.indexOf("SIGNATURE_BLACK"))
        assertThat(order.indexOf("SIGNATURE_BLACK")).isLessThan(order.indexOf("HOF"))
        assertThat(order).doesNotHaveDuplicates()
    }

    @Test
    fun `상대등급우세는 서열이 높을수록 낮아진다`() {
        // 상대가 내 카드보다 높을 확률이므로, 등급이 오를수록 단조 감소해야 한다.
        val table = ScoreCalculator.opponentGradeAdvantageProbabilitiesByCardType
        val values = CardRules.GRADES_LOW_TO_HIGH.map { grade ->
            assertThat(table).`as`("상대등급우세 표에 %s 누락", grade).containsKey(grade)
            table.getValue(grade)
        }

        assertThat(values).isSortedAccordingTo(compareByDescending { it })
        assertThat(values.first()).isGreaterThan(values.last())
        assertThat(table.getValue("HOF")).isEqualTo(0.00)
        assertThat(table.getValue("SEASON")).isEqualTo(table.getValue("LIVE"))
    }

    @Test
    fun `변형은 프라임 시그니처 시그니처블랙에만 붙는다`() {
        for (grade in listOf("PRIME", "SIGNATURE", "SIGNATURE_BLACK")) {
            CardRules.validateCombination(grade, "FA")
            CardRules.validateCombination(grade, "WBC")
        }
        for (grade in listOf("SEASON", "LIVE", "IMPACT", "MOMENT", "SUPREME_MOMENT", "HOF")) {
            CardRules.validateCombination(grade, "NONE")
            assertThatThrownBy { CardRules.validateCombination(grade, "WBC") }
                .`as`(grade)
                .hasMessageContaining("no WBC variant")
        }
    }

    @Test
    fun `WBC 변형은 등급과 무관하게 WBC 풀을 더한다`() {
        assertThat(CardRules.skillPools("PRIME", "WBC")).contains("WBC")
        assertThat(CardRules.skillPools("SIGNATURE", "WBC")).contains("WBC")
        assertThat(CardRules.skillPools("SIGNATURE_BLACK", "WBC")).contains("WBC", "BLACK")
        // FA는 전용 풀이 없다. 지금은 서열 표기용 이름일 뿐이다.
        assertThat(CardRules.skillPools("SIGNATURE", "FA"))
            .isEqualTo(CardRules.skillPools("SIGNATURE", "NONE"))
    }

    @Test
    fun `임팩트와 프라임은 아이언 브론즈 실버 골드만 가진다`() {
        for (grade in listOf("IMPACT", "PRIME", "LIVE", "SEASON")) {
            assertThat(CardRules.skillPools(grade, "NONE"))
                .`as`(grade)
                .containsExactly("NORMAL")
        }
    }

    @Test
    fun `지원하지 않는 등급과 변형은 거부한다`() {
        assertThatThrownBy { CardRules.normalizeGrade("NOPE") }
            .hasMessageContaining("Unsupported card grade")
        assertThatThrownBy { CardRules.normalizeVariant("KBO") }
            .hasMessageContaining("Unsupported card variant")
        assertThat(CardRules.normalizeVariant(null)).isEqualTo("NONE")
        assertThat(CardRules.normalizeVariant("  wbc ")).isEqualTo("WBC")
        // 예전 이름도 등급으로 받아 준다.
        assertThat(CardRules.normalizeGrade("wbc_black")).isEqualTo("SIGNATURE_BLACK")
    }
}
