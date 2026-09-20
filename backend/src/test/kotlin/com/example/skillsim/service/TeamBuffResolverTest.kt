package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.DeckSkillSelection
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.model.SkillBonus
import com.example.skillsim.model.SlotTraining
import com.example.skillsim.model.TeamBuffSkill
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * 라인업 전체를 올려 주는 스킬과, 그것이 만드는 능력치 점수.
 *
 * 골든값은 워크북이 스스로 캐시해 둔 계산 결과다. 워크북의 팀 설정을 우리 스킬 수치로
 * 옮겼을 때 같은 수가 나오는지를 본다 — 표를 옮겨 적으며 스탯별로 값이 갈리는 지점
 * (포수 리드 S는 구위에 0, WBC 에이스 S1은 파워 2·정확 1)을 놓치기 쉬운 자리다.
 */
class TeamBuffResolverTest {

    // 커밋된 CSV를 그대로 읽는다. 수치를 테스트에 옮겨 적으면 두 벌이 되어 서로 어긋난다.
    private val deckData = DeckDataLoader().apply { run() }
    private val skills = InMemoryScoreSkillRepository().also { ScoreDataLoader(it).run() }

    private fun resolver() = TeamBuffResolver(deckData.teamBuffSkills) { skills.findBySkillKey(it) }

    private fun player(
        slot: String,
        vararg selections: Pair<String, Int>,
        battingOrder: Int? = null,
    ) = DeckPlayer(
        slot = slot,
        position = DeckRules.positionForSlot(slot) ?: "C",
        cardGrade = "SIGNATURE",
        skills = selections.map { DeckSkillSelection(it.first, it.second) },
        battingOrder = battingOrder,
        pitcherSlot = DeckRules.pitcherSlotNumber(slot),
    )

    private fun roster(vararg players: DeckPlayer) =
        DeckRoster(starterCount = 5, closerCount = 1, players = players.toList())

    @Test
    fun `워크북의 타자 능력치 점수를 재현한다`() {
        // 워크북 J11 = 9.5. 파워·정확·선구가 각 1이고 타자 케미스트리 S1, WBC 에이스(타자) S1이다.
        val result = resolver().resolve(
            roster(
                // 타자 케미스트리(HOF_012) S1 = 레벨 6, WBC 에이스 타자(WBC_005) S1 = 레벨 2.
                player("C", "HOF_012" to 6, battingOrder = 1),
                player("1B", "WBC_005" to 2, battingOrder = 2),
            ),
            PositionTraining.EMPTY,
        )

        assertThat(result.batter).containsExactlyInAnyOrderEntriesOf(
            mapOf("파워" to 4.0, "정확" to 3.0),
        )

        val score = StatScoreCalculator { STAT_WEIGHTS }
            .score(mapOf("파워" to 1.0, "정확" to 1.0, "선구" to 1.0), result.batter, isPitcher = false)
        assertThat(score.total).isCloseTo(9.5, within(0.001))
    }

    @Test
    fun `워크북의 투수 능력치 점수를 재현한다`() {
        // 워크북 J22 = 11.75. 변화·구위가 각 1이고 커맨더 S, 포수 리드 S, 투수 케미스트리 S,
        // WBC 에이스(투수) S다.
        val result = resolver().resolve(
            roster(
                // 커맨더(M_014)와 포수 리드(G_027)는 포수 자리에서만 팀 투수를 올린다.
                player("C", "M_014" to 1, "G_027" to 5, battingOrder = 1),
                player("SP1", "HOF_031" to 5),
                player("SP2", "WBC_011" to 1),
            ),
            PositionTraining.EMPTY,
        )

        // 커맨더 변화1·구위2 + 포수리드 변화1·구위0 + 투케 변화1·구위1 + WBC 변화1·구위1.
        assertThat(result.pitcher).containsExactlyInAnyOrderEntriesOf(
            mapOf("변화" to 4.0, "구위" to 4.0),
        )

        val score = StatScoreCalculator { STAT_WEIGHTS }
            .score(mapOf("변화" to 1.0, "구위" to 1.0), result.pitcher, isPitcher = true)
        assertThat(score.total).isCloseTo(11.75, within(0.001))
    }

    @Test
    fun `포수 리드 S는 변화만 올리고 구위는 올리지 않는다`() {
        // 워크북 수식의 중첩 IF가 구위만 S1부터 시작한다. 대칭으로 옮겨 적으면 틀린다.
        val buffs = deckData.teamBuffSkills.getValue("G_027").associateBy { it.stat }

        assertThat(buffs.getValue("변화").at(5)).isEqualTo(1.0)
        assertThat(buffs.getValue("구위").at(5)).isEqualTo(0.0)
        assertThat(buffs.getValue("구위").at(6)).isEqualTo(1.0)
    }

    @Test
    fun `WBC 에이스 타자 S1은 파워를 2 정확을 1 올린다`() {
        val buffs = deckData.teamBuffSkills.getValue("WBC_005").associateBy { it.stat }

        assertThat(buffs.getValue("파워").at(2)).isEqualTo(2.0)
        assertThat(buffs.getValue("정확").at(2)).isEqualTo(1.0)
    }

    @Test
    fun `커맨더는 포수 자리에 있어야 팀 투수를 올린다`() {
        val elsewhere = resolver().resolve(
            roster(player("1B", "M_014" to 1, battingOrder = 1)),
            PositionTraining.EMPTY,
        )
        assertThat(elsewhere.pitcher).isEmpty()

        val atCatcher = resolver().resolve(
            roster(player("C", "M_014" to 1, battingOrder = 1)),
            PositionTraining.EMPTY,
        )
        assertThat(atCatcher.pitcher).isNotEmpty
    }

    @Test
    fun `후보는 주기만 하고 받지는 않는다`() {
        val result = resolver().resolve(
            roster(player("BENCH1", "HOF_012" to 6)),
            PositionTraining.EMPTY,
        )

        assertThat(result.batter).isNotEmpty
        assertThat(result.forPlayer("BENCH1")).isEmpty()
        assertThat(result.forPlayer("C")).isNotEmpty
        assertThat(result.forPlayer("SP1")).isEmpty()
    }

    @Test
    fun `같은 스킬을 여럿이 들면 가장 높은 레벨 하나만 센다`() {
        // 설명문이 "동일 스킬과 중복 불가"라고 적었다.
        val result = resolver().resolve(
            roster(
                player("C", "HOF_012" to 1, battingOrder = 1),
                player("1B", "HOF_012" to 6, battingOrder = 2),
            ),
            PositionTraining.EMPTY,
        )

        assertThat(result.batter.getValue("파워")).isEqualTo(2.0)
        assertThat(result.sources).hasSize(1)
        assertThat(result.sources.first().level).isEqualTo(6)
    }

    @Test
    fun `포지션 훈련이 얹어 준 레벨까지 반영한다`() {
        // 보너스는 자리에 붙으므로 그 자리에 선 사람이 받는다.
        val training = PositionTraining(
            slots = mapOf("C" to SlotTraining(skills = listOf(SkillBonus("HOF_012", 2)))),
        )
        val result = resolver().resolve(
            roster(player("C", "HOF_012" to 4, battingOrder = 1)),
            training,
        )

        // 4 + 2 = 6 -> 사다리 마지막 칸 2.
        assertThat(result.sources.single().level).isEqualTo(6)
        assertThat(result.batter.getValue("파워")).isEqualTo(2.0)
    }

    @Test
    fun `팀 버프 표가 여섯 스킬을 덮는다`() {
        assertThat(deckData.teamBuffSkills.keys).containsExactlyInAnyOrder(
            "HOF_012", "HOF_031", "WBC_005", "WBC_011", "M_014", "G_027",
        )
        assertThat(deckData.teamBuffSkills.values.flatten())
            .allSatisfy { assertThat(it.scope).isIn(*TeamBuffSkill.Scope.entries.toTypedArray()) }
    }

    private companion object {
        /** 워크북 `스킬계수` 표와 같은 값이다. stat_weights.csv도 이 값을 쓴다. */
        val STAT_WEIGHTS = mapOf(
            "파워" to 1.10,
            "정확" to 0.90,
            "선구" to 0.40,
            "구위" to 1.20,
            "변화" to 1.15,
        )
    }
}
