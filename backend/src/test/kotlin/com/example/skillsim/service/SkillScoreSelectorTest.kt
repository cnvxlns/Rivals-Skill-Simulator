package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 워크북 옵션 변형을 고르는 판정식.
 *
 * 모르는 판정식이 조용히 "맞음"으로 떨어지면 점수가 틀린다. 그래서 판정할 수 없는 경우는
 * 전부 거짓이어야 한다 — 이 클래스가 주로 지키는 것이 그 쪽이다.
 */
class SkillScoreSelectorTest {

    private fun context(
        position: String = "C",
        grade: String = "SIGNATURE",
        battingOrder: Int? = null,
        pitcherSlot: Int? = null,
        relieverRole: RelieverRole? = null,
        batHand: Handedness? = null,
        throwHand: Handedness? = null,
        finalStats: Map<String, Double> = emptyMap(),
        baseStats: Map<String, Double> = emptyMap(),
    ) = SkillScoreSelector.Context(
        position, grade, battingOrder, pitcherSlot, relieverRole, batHand, throwHand, finalStats, baseStats,
    )

    @Test
    fun `기본값과 손으로 고르는 값`() {
        assertThat(SkillScoreSelector.matches("ALWAYS", context())).isTrue()
        assertThat(SkillScoreSelector.matches("MANUAL", context())).isFalse()
        assertThat(SkillScoreSelector.matches("", context())).isFalse()
    }

    @Test
    fun `모르는 판정식은 절대 맞지 않는다`() {
        assertThat(SkillScoreSelector.matches("LAUNCH_ANGLE=10..14", context())).isFalse()
        assertThat(SkillScoreSelector.matches("ORDER", context())).isFalse()
        assertThat(SkillScoreSelector.matches("!NOPE=1", context())).isFalse()
    }

    @Test
    fun `타순 구간`() {
        assertThat(SkillScoreSelector.matches("ORDER=1..2", context(battingOrder = 2))).isTrue()
        assertThat(SkillScoreSelector.matches("ORDER=1..2", context(battingOrder = 3))).isFalse()
        assertThat(SkillScoreSelector.matches("ORDER=3", context(battingOrder = 3))).isTrue()
        // 타순이 없으면 참도 거짓도 아니다. 뒤집어도 맞지 않는다.
        assertThat(SkillScoreSelector.matches("ORDER=1..2", context())).isFalse()
        assertThat(SkillScoreSelector.matches("!ORDER=1..2", context())).isFalse()
    }

    @Test
    fun `부정은 아는 값에만 걸린다`() {
        assertThat(SkillScoreSelector.matches("!ORDER=1..2", context(battingOrder = 5))).isTrue()
        assertThat(SkillScoreSelector.matches("!BAT=LEFT", context(batHand = Handedness.RIGHT))).isTrue()
        assertThat(SkillScoreSelector.matches("!BAT=LEFT", context())).isFalse()
    }

    @Test
    fun `포지션과 역할`() {
        assertThat(SkillScoreSelector.matches("POS=OF", context(position = "CF"))).isTrue()
        assertThat(SkillScoreSelector.matches("POS=OF", context(position = "SS"))).isFalse()
        assertThat(SkillScoreSelector.matches("ROLE=SP", context(position = "SP"))).isTrue()
        assertThat(SkillScoreSelector.matches("ROLE=RP|CP", context(position = "CP"))).isTrue()
        assertThat(SkillScoreSelector.matches("ROLE=RP|CP", context(position = "SP"))).isFalse()
    }

    @Test
    fun `투수 슬롯과 중계 역할`() {
        assertThat(SkillScoreSelector.matches("PSLOT=1..2", context(pitcherSlot = 2))).isTrue()
        assertThat(SkillScoreSelector.matches("PSLOT=1..2", context(pitcherSlot = 3))).isFalse()
        assertThat(SkillScoreSelector.matches("RELIEVER=WIN", context(relieverRole = RelieverRole.WIN))).isTrue()
        assertThat(SkillScoreSelector.matches("RELIEVER=WIN", context(relieverRole = RelieverRole.LONG))).isFalse()
    }

    @Test
    fun `카드 등급`() {
        assertThat(SkillScoreSelector.matches("GRADE=HOF", context(grade = "HOF"))).isTrue()
        assertThat(SkillScoreSelector.matches("!GRADE=HOF", context(grade = "SIGNATURE"))).isTrue()
    }

    @Test
    fun `능력치 구간`() {
        val stats = mapOf("변화" to 162.0, "정확" to 210.0)

        assertThat(SkillScoreSelector.matches("STAT(변화)=150..199", context(finalStats = stats))).isTrue()
        assertThat(SkillScoreSelector.matches("STAT(변화)=200..249", context(finalStats = stats))).isFalse()
        assertThat(SkillScoreSelector.matches("STAT(변화)>=150", context(finalStats = stats))).isTrue()
        assertThat(SkillScoreSelector.matches("STAT(변화)<150", context(finalStats = stats))).isFalse()
        // 값을 모르면 맞지 않는다.
        assertThat(SkillScoreSelector.matches("STAT(구위)=150..199", context(finalStats = stats))).isFalse()
    }

    @Test
    fun `합산 능력치는 성분을 더한다`() {
        val stats = mapOf("주루" to 75.0, "수비" to 90.0)

        assertThat(SkillScoreSelector.matches("BASESTAT(주루+수비)>=165", context(baseStats = stats))).isTrue()
        assertThat(SkillScoreSelector.matches("BASESTAT(주루+수비)<155", context(baseStats = stats))).isFalse()
        // 성분이 하나라도 없으면 판정하지 않는다.
        assertThat(
            SkillScoreSelector.matches("BASESTAT(주루+수비)>=165", context(baseStats = mapOf("주루" to 75.0))),
        ).isFalse()
    }

    @Test
    fun `여러 조건은 모두 맞아야 한다`() {
        val ctx = context(battingOrder = 2, batHand = Handedness.LEFT, finalStats = mapOf("정확" to 210.0))

        assertThat(SkillScoreSelector.matches("BAT=LEFT&ORDER=2", ctx)).isTrue()
        assertThat(SkillScoreSelector.matches("BAT=LEFT&!ORDER=2", ctx)).isFalse()
        assertThat(SkillScoreSelector.matches("ORDER=1..2&STAT(정확)=200..233", ctx)).isTrue()
    }

    @Test
    fun `읽을 수 있는 문법과 없는 문법을 가른다`() {
        assertThat(SkillScoreSelector.isReadable("ALWAYS")).isTrue()
        assertThat(SkillScoreSelector.isReadable("MANUAL")).isTrue()
        assertThat(SkillScoreSelector.isReadable("!ORDER=1..2&STAT(정확)=200..233")).isTrue()
        assertThat(SkillScoreSelector.isReadable("LAUNCH_ANGLE=10..14")).isFalse()
        assertThat(SkillScoreSelector.isReadable("")).isFalse()
    }

    @Test
    fun `커밋된 점수표의 판정식이 전부 읽히는 문법이다`() {
        // 읽을 수 없는 판정식은 영영 맞지 않아 조용히 엔진 값으로 떨어진다. 여기서 잡는다.
        val rows = DeckDataLoader().apply { run() }.excelSkillScores.values.flatten()
        assertThat(rows).isNotEmpty

        val unreadable = rows.map { it.selector }.distinct()
            .filterNot { SkillScoreSelector.isReadable(it) }
        assertThat(unreadable).isEmpty()
    }

    @Test
    fun `표의 각 칸에는 기본값이 하나뿐이고 맨 뒤에 있다`() {
        // 기본값이 먼저 나오면 뒤의 좁은 판정식이 영영 안 걸린다.
        val table = DeckDataLoader().apply { run() }.excelSkillScores

        assertThat(table).isNotEmpty
        table.forEach { (key, rows) ->
            val defaults = rows.withIndex().filter { it.value.selector == SkillScoreSelector.ALWAYS }
            assertThat(defaults.size).describedAs("$key").isLessThanOrEqualTo(1)
            defaults.firstOrNull()?.let {
                assertThat(it.index).describedAs("$key").isEqualTo(rows.lastIndex)
            }
        }
    }
}
