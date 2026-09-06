package com.example.skillsim.service

import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.DeckRoster
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class DeckScoreServiceTest {

    private val repository = DeckFixtures.repository()
    private val scoreService = ScoreService(
        repository, ScoreCalculator(), mapOf(DeckFixtures.STAT to 1.0),
    )
    private val deckScoreService = DeckScoreService(repository, scoreService)
    private val validator = DeckFixtures.validator(repository)

    private fun roster(): DeckRoster = validator.validate(DeckFixtures.deckRequest())

    @Test
    fun `파트별 소계를 더하면 종합 점수가 된다`() {
        val result = deckScoreService.score(roster())

        assertThat(result.players).hasSize(DeckRoster.ROSTER_SIZE)
        assertThat(result.parts.sumOf { it.total })
            .isCloseTo(result.total, within(0.01))
        assertThat(result.parts.sumOf { it.playerCount }).isEqualTo(DeckRoster.ROSTER_SIZE)
    }

    @Test
    fun `파트는 타선 후보 선발진 불펜진으로 나뉜다`() {
        val result = deckScoreService.score(roster())
        val byPart = result.parts.associateBy { it.part }

        // 선발 5 · 마무리 2 → 중계 5. 불펜진은 중계와 마무리를 합친 7명이다.
        assertThat(byPart.getValue("LINEUP").playerCount).isEqualTo(9)
        assertThat(byPart.getValue("BENCH").playerCount).isEqualTo(5)
        assertThat(byPart.getValue("ROTATION").playerCount).isEqualTo(5)
        assertThat(byPart.getValue("BULLPEN").playerCount).isEqualTo(7)
    }

    @Test
    fun `중계 하위 역할은 점수를 바꾸지 않는다`() {
        // 요구사항의 실행 가능한 증명이다. 조건 게이트가 슬롯 번호만 보므로
        // 승리조/추격조/롱릴리프를 어떻게 배분해도 점수가 같아야 한다.
        val base = roster()
        val flipped = base.copy(
            players = base.players.map { player ->
                if (player.relieverRole == null) {
                    player
                } else {
                    player.copy(relieverRole = RelieverRole.WIN)
                }
            },
        )

        assertThat(flipped.players.count { it.relieverRole == RelieverRole.WIN }).isEqualTo(5)
        assertThat(deckScoreService.score(flipped).total)
            .isEqualTo(deckScoreService.score(base).total)
    }

    @Test
    fun `선수 목록은 기여도 내림차순이다`() {
        val scores = deckScoreService.score(roster()).players.map { it.score }

        assertThat(scores).isSortedAccordingTo(compareByDescending { it })
    }

    @Test
    fun `자리 정보가 결과에 그대로 실린다`() {
        val result = deckScoreService.score(roster())
        val bySlot = result.players.associateBy { it.slot }

        assertThat(bySlot.getValue("SS").battingOrder).isEqualTo(5)
        assertThat(bySlot.getValue("SS").pitcherSlot).isNull()
        assertThat(bySlot.getValue("RP2").pitcherSlot).isEqualTo(2)
        assertThat(bySlot.getValue("RP2").relieverRole).isEqualTo(RelieverRole.LONG)
        assertThat(bySlot.getValue("BENCH1").battingOrder).isNull()
    }

    @Test
    fun `기본 응답에는 효과 단위 근거를 담지 않는다`() {
        // 26명 x 3~4스킬 x 여러 효과면 응답이 수백 KB로 불어난다.
        val lean = deckScoreService.score(roster())
        assertThat(lean.players.flatMap { it.perSkill }).isNotEmpty
        assertThat(lean.players.flatMap { it.perSkill }.flatMap { it.breakdown }).isEmpty()

        val detailed = deckScoreService.score(roster(), includeBreakdown = true)
        assertThat(detailed.players.flatMap { it.perSkill }.flatMap { it.breakdown }).isNotEmpty
        assertThat(detailed.total).isEqualTo(lean.total)
    }

    @Test
    fun `타순이 없는 후보도 채점된다`() {
        // ScoreService.calculate는 타자에게 타순을 요구하지만, 후보는 타순이 없다.
        // scoreSelections를 직접 쓰면 기본 타순으로 떨어져 점수가 나온다.
        val bench = deckScoreService.score(roster()).players.filter { it.slot.startsWith("BENCH") }

        assertThat(bench).hasSize(5)
        assertThat(bench).allSatisfy { assertThat(it.score).isGreaterThan(0.0) }
    }
}
