package com.example.skillsim.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CollectionBuffTest {

    private fun cards(grade: String, count: Int) = List(count) { grade }

    private fun bonusOf(grades: List<String>, family: CollectionBuff.Family) =
        CollectionBuff.of(grades).families.first { it.family == family }

    @Test
    fun `임계값을 넘긴 수만큼 누적해서 오른다`() {
        // 모먼트는 2·5·8·11·14에서 오른다. 9장이면 2·5·8 셋을 넘겨 +3이다.
        val nine = bonusOf(cards("MOMENT", 9), CollectionBuff.Family.MOMENT)

        assertThat(nine.count).isEqualTo(9)
        assertThat(nine.batterBonus).isEqualTo(3)
        assertThat(nine.pitcherBonus).isEqualTo(3)

        // 한 장 모자라면 그 단계는 걸리지 않는다.
        assertThat(bonusOf(cards("MOMENT", 1), CollectionBuff.Family.MOMENT).batterBonus).isEqualTo(0)
        assertThat(bonusOf(cards("MOMENT", 14), CollectionBuff.Family.MOMENT).batterBonus).isEqualTo(5)
    }

    @Test
    fun `슈프림 모먼트 한 장은 모먼트 두 장으로 센다`() {
        // 슈모 4장 + 모먼트 3장 = 가중치 11 → 2·5·8·11을 모두 넘겨 +4.
        val grades = cards("SUPREME_MOMENT", 4) + cards("MOMENT", 3)
        val moment = bonusOf(grades, CollectionBuff.Family.MOMENT)

        assertThat(moment.count).isEqualTo(11)
        assertThat(moment.batterBonus).isEqualTo(4)

        // 장수로 셌다면 7장이라 2·5만 넘겨 +2에 그친다. 가중치 합이라는 점이 결과를 가른다.
        assertThat(bonusOf(cards("MOMENT", 7), CollectionBuff.Family.MOMENT).batterBonus).isEqualTo(2)
    }

    @Test
    fun `시그니처 블랙은 시그니처 계열로 센다`() {
        val grades = cards("SIGNATURE", 3) + cards("SIGNATURE_BLACK", 2)
        val signature = bonusOf(grades, CollectionBuff.Family.SIGNATURE)

        assertThat(signature.count).isEqualTo(5)
        assertThat(signature.batterBonus).isEqualTo(2)
    }

    @Test
    fun `변형은 등급을 바꾸지 않으므로 같은 계열로 센다`() {
        // 예전 단일 카드 타입 이름으로 저장된 덱도 등급으로 풀려 같은 계열에 들어간다.
        assertThat(CollectionBuff.familyOf("WBC")).isEqualTo(CollectionBuff.Family.SIGNATURE)
        assertThat(CollectionBuff.familyOf("WBC_BLACK")).isEqualTo(CollectionBuff.Family.SIGNATURE)
        assertThat(CollectionBuff.familyOf("NORMAL")).isEqualTo(CollectionBuff.Family.SIGNATURE)
    }

    @Test
    fun `시즌은 타자와 투수가 서로 다른 단계에서 오른다`() {
        // 타자 6·16, 투수 10·20.
        val six = bonusOf(cards("SEASON", 6), CollectionBuff.Family.SEASON)
        assertThat(six.batterBonus).isEqualTo(1)
        assertThat(six.pitcherBonus).isEqualTo(0)

        val ten = bonusOf(cards("SEASON", 10), CollectionBuff.Family.SEASON)
        assertThat(ten.batterBonus).isEqualTo(1)
        assertThat(ten.pitcherBonus).isEqualTo(1)

        val sixteen = bonusOf(cards("SEASON", 16), CollectionBuff.Family.SEASON)
        assertThat(sixteen.batterBonus).isEqualTo(2)
        assertThat(sixteen.pitcherBonus).isEqualTo(1)

        val twenty = bonusOf(cards("SEASON", 20), CollectionBuff.Family.SEASON)
        assertThat(twenty.batterBonus).isEqualTo(2)
        assertThat(twenty.pitcherBonus).isEqualTo(2)
    }

    @Test
    fun `임팩트와 프라임은 어느 계열에도 들어가지 않는다`() {
        assertThat(CollectionBuff.familyOf("IMPACT")).isNull()
        assertThat(CollectionBuff.familyOf("PRIME")).isNull()

        val result = CollectionBuff.of(cards("IMPACT", 13) + cards("PRIME", 13))

        assertThat(result.batterBonus).isEqualTo(0)
        assertThat(result.pitcherBonus).isEqualTo(0)
        assertThat(result.families).allMatch { it.count == 0 }
    }

    @Test
    fun `계열끼리도 누적된다`() {
        // HOF 8(+3) + 시그니처 8(+3) + 라이브 4(+2) + 시즌 6(타자만 +1) = 26장.
        val grades = cards("HOF", 8) + cards("SIGNATURE", 8) + cards("LIVE", 4) + cards("SEASON", 6)
        val result = CollectionBuff.of(grades)

        assertThat(grades).hasSize(26)
        assertThat(result.batterBonus).isEqualTo(9)
        assertThat(result.pitcherBonus).isEqualTo(8)
        assertThat(result.bonusFor(isPitcher = true)).isEqualTo(8)
        assertThat(result.bonusFor(isPitcher = false)).isEqualTo(9)
    }

    @Test
    fun `빈 덱은 아무 버프도 주지 않는다`() {
        val result = CollectionBuff.of(emptyList())

        assertThat(result.batterBonus).isEqualTo(0)
        assertThat(result.pitcherBonus).isEqualTo(0)
        // 계열은 0장이라도 전부 내려보낸다. 화면이 목록을 그대로 그릴 수 있게 하기 위해서다.
        assertThat(result.families).hasSize(CollectionBuff.Family.entries.size)
    }
}
