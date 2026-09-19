package com.example.skillsim.service

import com.example.skillsim.enums.TicketType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class RollTablesTest {

    /**
     * 확률표는 합이 100이어야 한다.
     *
     * 걷어냈던 자바 표는 이 불변식을 암묵적으로만 갖고 있었다. 한 줄을 고치다 합이 어긋나면
     * 뽑기가 조용히 편향되고, 기댓값은 그럴듯한 숫자로 나와서 알아채기 어렵다.
     */
    @Test
    fun `모든 티어 표의 합은 100이다`() {
        RollTables.ALL_TIER_TABLES.forEach { (name, table) ->
            val total = table.values.sumOf { levels -> levels.values.sum() }
            assertThat(total).describedAs(name).isCloseTo(100.0, within(1e-6))
        }
    }

    @Test
    fun `모먼트 첫 슬롯 표도 합이 100이다`() {
        listOf("MOMENT", "SUPREME_MOMENT").forEach { grade ->
            val table = RollTables.momentSlotOneTable(grade)
            val total = table.values.sumOf { it.values.sum() }
            assertThat(total).describedAs(grade).isCloseTo(100.0, within(1e-6))
        }
    }

    /** 공식 확률 페이지의 `SUPREME M SUPERIOR SKILL CHANGE`가 모먼트 30%로 적혀 있다. */
    @Test
    fun `슈프림 모먼트는 첫 슬롯 모먼트 확률이 일반 모먼트보다 높다`() {
        assertThat(RollTables.momentSlotOneChance("SUPREME_MOMENT")).isEqualTo(0.30)
        assertThat(RollTables.momentSlotOneChance("MOMENT")).isEqualTo(0.06)
    }

    /**
     * 공식 확률 페이지의 시트 168·169 값과 맞는지 못을 박는다.
     *
     * 일반 카드용 시트는 `SKILL CHANGE EVENT` 하나뿐이라 일반권과 고급권이 같은 표를 쓴다.
     * 두 티켓의 차이는 전용 티어(HOF·블랙·WBC) 지분에서만 생긴다.
     */
    @Test
    fun `일반 카드의 티어 확률은 일반권과 고급권이 같다`() {
        val basic = RollTables.tierTable(RollFamily.NORMAL, TicketType.SKILL_CHANGE, 1)
        val premium = RollTables.tierTable(RollFamily.NORMAL, TicketType.PREMIUM_SKILL_CHANGE, 1)
        assertThat(premium).isEqualTo(basic)

        assertThat(tierTotal(basic, SkillTier.IRON)).isCloseTo(35.0, within(1e-6))
        assertThat(tierTotal(basic, SkillTier.BRONZE)).isCloseTo(30.0, within(1e-6))
        assertThat(tierTotal(basic, SkillTier.SILVER)).isCloseTo(20.0, within(1e-6))
        assertThat(tierTotal(basic, SkillTier.GOLD)).isCloseTo(15.0, within(1e-6))
    }

    /** 최고급은 아이언이 빠진다(v3.02.00 "아이언 티어 스킬 등장 확률 제거"). */
    @Test
    fun `최고급은 아이언이 나오지 않는다`() {
        val supreme = RollTables.tierTable(RollFamily.NORMAL, TicketType.SUPREME_SKILL_CHANGE, 1)

        assertThat(supreme).doesNotContainKey(SkillTier.IRON)
        assertThat(tierTotal(supreme, SkillTier.BRONZE)).isCloseTo(35.0, within(1e-6))
        assertThat(tierTotal(supreme, SkillTier.SILVER)).isCloseTo(40.0, within(1e-6))
        assertThat(tierTotal(supreme, SkillTier.GOLD)).isCloseTo(25.0, within(1e-6))
    }

    /** HOF 카드에서만 일반권과 고급권의 티어 확률이 갈린다. */
    @Test
    fun `HOF 티어 지분은 티켓에 따라 다르다`() {
        val basic = RollTables.tierTable(RollFamily.HOF, TicketType.SKILL_CHANGE, 1)
        val premium = RollTables.tierTable(RollFamily.HOF, TicketType.PREMIUM_SKILL_CHANGE, 1)
        val supreme = RollTables.tierTable(RollFamily.HOF, TicketType.SUPREME_SKILL_CHANGE, 1)

        assertThat(tierTotal(basic, SkillTier.HOF)).isCloseTo(0.1, within(1e-6))
        assertThat(tierTotal(premium, SkillTier.HOF)).isCloseTo(1.5, within(1e-6))
        assertThat(tierTotal(supreme, SkillTier.HOF)).isCloseTo(10.0, within(1e-6))
    }

    /** 최고급 첫 슬롯은 골드가 보장된다. HOF 카드에서는 골드 아니면 HOF다. */
    @Test
    fun `HOF 최고급 첫 슬롯에는 아이언과 브론즈가 없다`() {
        val slotOne = RollTables.tierTable(RollFamily.HOF, TicketType.SUPREME_SKILL_CHANGE, 0)

        assertThat(slotOne.keys).containsExactlyInAnyOrder(SkillTier.GOLD, SkillTier.HOF)
        assertThat(tierTotal(slotOne, SkillTier.GOLD)).isCloseTo(90.0, within(1e-6))
    }

    @Test
    fun `등급과 변형을 롤 묶음으로 푼다`() {
        assertThat(RollFamily.of("SIGNATURE", "NONE")).isEqualTo(RollFamily.NORMAL)
        assertThat(RollFamily.of("IMPACT", "NONE")).isEqualTo(RollFamily.NORMAL)
        assertThat(RollFamily.of("SIGNATURE", "WBC")).isEqualTo(RollFamily.WBC)
        assertThat(RollFamily.of("SIGNATURE_BLACK", "NONE")).isEqualTo(RollFamily.BLACK)
        assertThat(RollFamily.of("SIGNATURE_BLACK", "WBC")).isEqualTo(RollFamily.WBC_BLACK)
        assertThat(RollFamily.of("SUPREME_MOMENT", "NONE")).isEqualTo(RollFamily.MOMENT)
        assertThat(RollFamily.of("HOF", "NONE")).isEqualTo(RollFamily.HOF)

        // 예전 단일 이름으로 저장된 값도 같은 곳으로 떨어진다.
        assertThat(RollFamily.of("WBC", null)).isEqualTo(RollFamily.WBC)
        assertThat(RollFamily.of("WBC_BLACK", null)).isEqualTo(RollFamily.WBC_BLACK)
    }

    private fun tierTotal(table: Map<SkillTier, Map<com.example.skillsim.enums.Level, Double>>, tier: SkillTier) =
        table[tier]?.values?.sum() ?: 0.0
}
