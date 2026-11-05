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
    fun `투수의 팀 타자 버프는 타자에게 가고 리그 모드 전용 버프는 랭킹대전에서 0이다`() {
        // 퍼포머트리오(투수) M_026: "팀 타자 전체의 파워, 정확 +2".
        val performer = resolver().resolve(roster(player("SP1", "M_026" to 1)), PositionTraining.EMPTY)
        assertThat(performer.batter).containsEntry("파워", 2.0).containsEntry("정확", 2.0)
        assertThat(performer.pitcher).isEmpty()

        // 리그의 지배자 HOF_003: "리그 모드에서 라인업 모든 타자 파워, 정확 +1".
        val league = resolver().resolve(roster(player("C", "HOF_003" to 5, battingOrder = 1)), PositionTraining.EMPTY)
        assertThat(league.batter).isEmpty()
    }

    @Test
    fun `컴백 플래그는 RP가 보유할 때 동점 또는 열세 비율만큼 팀 타자를 올린다`() {
        val ladder = listOf(1, 1, 2, 2, 3, 3, 4)
        for (level in 1..7) {
            val result = resolver().resolve(roster(player("RP1", "BLACK_027" to level)), PositionTraining.EMPTY)
            val expected = ladder[level - 1] * ScoreCalculator.gameStateProbabilities.getValue("비김또는열세")
            assertThat(result.batter.getValue("파워")).isCloseTo(expected, within(1e-9))
            assertThat(result.batter.getValue("정확")).isCloseTo(expected, within(1e-9))
            assertThat(result.pitcher).isEmpty()
            assertThat(result.forPlayer("C")).isEqualTo(result.batter)
            assertThat(result.forPlayer("RP1")).isEmpty()
        }
        for (slot in listOf("SP1", "CP1", "C")) {
            val result = resolver().resolve(roster(player(slot, "BLACK_027" to 5)), PositionTraining.EMPTY)
            assertThat(result.batter).isEmpty()
            assertThat(result.sources).isEmpty()
        }
        val mixed = resolver().resolve(
            roster(player("SP1", "BLACK_027" to 7), player("RP1", "BLACK_027" to 5)),
            PositionTraining.EMPTY,
        )
        assertThat(mixed.sources.single().slot).isEqualTo("RP1")
        assertThat(mixed.batter.getValue("파워")).isCloseTo(1.89, within(1e-9))

    }

    @Test
    fun `팀 버프 행은 보유자 본인의 효과에 들어 있지 않다`() {
        // 계산기와 점수표는 본인 효과만 채점한다. 팀 효과가 효과행에도 있으면 거기서 보유자 점수로 잡힌다.
        for ((skillId, buffs) in deckData.teamBuffSkills) {
            val own = skills.findBySkillKey(skillId)?.effects.orEmpty()
                .map { Triple(it.stat, it.condition, it.values) }
            for (buff in buffs) {
                assertThat(own).describedAs(skillId).doesNotContain(Triple(buff.stat, buff.condition, buff.rawValues))
            }
        }
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
    fun `팀 버프 표가 팀 전체 효과를 가진 스킬을 모두 덮는다`() {
        assertThat(deckData.teamBuffSkills.keys).containsExactlyInAnyOrder(
            "HOF_012", "HOF_031", "WBC_005", "WBC_011", "M_014", "G_027",
            "M_006", "M_026", "G_041", "HOF_003", "HOF_021", "BLACK_027",
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
