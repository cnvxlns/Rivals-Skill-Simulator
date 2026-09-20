package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.dto.DeckPlayerRequest
import com.example.skillsim.dto.DeckSkillRequest
import com.example.skillsim.dto.PositionTrainingRequest
import com.example.skillsim.dto.SkillLevelBonus
import com.example.skillsim.dto.SlotTrainingRequest
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.ScoreEffect
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.model.SkillBonus
import com.example.skillsim.model.SlotTraining
import com.example.skillsim.repository.PositionTrainingRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * 구단의 포지션 훈련이 덱 채점에 들어가는 길.
 *
 * 훈련은 선수가 아니라 자리에 붙는다. 그래서 그 자리에 선 선수가 스킬 레벨 보너스를 받고,
 * 능력치는 "적을 당시 자리"와의 차이만 움직인다.
 */
class PositionTrainingDeckTest {

    /** 기준 스탯에 비례하는 효과. 능력치 보정이 점수에 드러나려면 이런 스킬이 있어야 한다. */
    private val proportional = ScoreSkill(
        skillKey = "G_010",
        cardType = "NORMAL",
        position = "BATTER, PITCHER",
        name = "비례",
        description = "비례",
        effects = mutableListOf(
            ScoreEffect(
                stat = DeckFixtures.STAT,
                condition = "ALWAYS",
                values = "1/1/1",
                baseStat = DeckFixtures.STAT,
            ),
        ),
    )

    private val repository = DeckFixtures.repository(listOf(proportional))
    private val scoreService = ScoreService(repository, ScoreCalculator(), mapOf(DeckFixtures.STAT to 1.0))
    private val deckScoreService = DeckScoreService(repository, scoreService, DeckDataLoader(), mapOf(DeckFixtures.STAT to 1.0))
    private val validator = DeckFixtures.validator(repository)
    private val trainingValidator = PositionTrainingValidator(repository, { setOf(DeckFixtures.STAT) })

    private fun roster(mutate: (DeckPlayerRequest) -> DeckPlayerRequest = { it }) =
        validator.validate(DeckFixtures.deckRequest(mutate = mutate))

    private fun scoreOf(
        training: PositionTraining,
        slot: String,
        mutate: (DeckPlayerRequest) -> DeckPlayerRequest = { it },
    ) = deckScoreService.score(roster(mutate), training = training)
        .players.first { it.slot == slot }.score

    @Test
    fun `자리에 붙은 보너스를 그 자리의 선수가 받는다`() {
        val training = PositionTraining(
            mapOf("C" to SlotTraining(skills = listOf(SkillBonus("G_001", 2)))),
        )

        val plain = scoreOf(PositionTraining.EMPTY, "C")
        val boosted = scoreOf(training, "C")

        // 사다리가 1/2/3이고 레벨 1로 끼웠으니 +2면 3이다. 가중치 1.0이라 점수 차가 곧 2다.
        assertThat(boosted - plain).isCloseTo(2.0, within(0.01))
        // 다른 자리는 그대로다.
        assertThat(scoreOf(training, "1B")).isCloseTo(scoreOf(PositionTraining.EMPTY, "1B"), within(0.01))
    }

    /**
     * 보유 능력치에는 적을 당시 자리의 포훈이 이미 들어 있다.
     *
     * 포수 자리에서 적은 선수를 1루에 세우면 포훈이 그 자리 것으로 갈아타므로 차이만 움직인다.
     */
    @Test
    fun `자리를 옮기면 포훈 능력치의 차이만큼만 달라진다`() {
        val training = PositionTraining(
            mapOf(
                "C" to SlotTraining(stats = mapOf(DeckFixtures.STAT to 10.0)),
                "1B" to SlotTraining(stats = mapOf(DeckFixtures.STAT to 4.0)),
            ),
        )
        // 1루수만 비례 스킬을 들게 하고, 능력치는 포수 자리에서 적었다고 한다.
        val movedFromCatcher = { player: DeckPlayerRequest ->
            if (player.slot == "1B") {
                player.copy(
                    skills = listOf(
                        DeckSkillRequest("G_010", 1),
                        DeckSkillRequest("G_002", 1),
                        DeckSkillRequest("G_003", 1),
                    ),
                    statsSlot = "C",
                )
            } else {
                player
            }
        }
        val stayed = { player: DeckPlayerRequest ->
            movedFromCatcher(player).let { if (it.slot == "1B") it.copy(statsSlot = null) else it }
        }

        val moved = scoreOf(training, "1B", movedFromCatcher)
        val same = scoreOf(training, "1B", stayed)

        // 비례 효과가 기준 스탯을 그대로 쓰므로, 포훈 차이 -6이 점수에 그대로 나타난다.
        assertThat(moved - same).isCloseTo(-6.0, within(0.01))
    }

    @Test
    fun `훈련이 없으면 채점이 지금까지와 같다`() {
        val plain = deckScoreService.score(roster())
        val empty = deckScoreService.score(roster(), training = PositionTraining.EMPTY)

        assertThat(empty.total).isEqualTo(plain.total)
    }

    @Test
    fun `모르는 자리와 모르는 스탯은 거절한다`() {
        assertThatThrownBy {
            trainingValidator.validate(
                PositionTrainingRequest(mapOf("LF2" to SlotTrainingRequest(stats = mapOf(DeckFixtures.STAT to 1.0)))),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unknown position training slot")

        assertThatThrownBy {
            trainingValidator.validate(
                PositionTrainingRequest(mapOf("C" to SlotTrainingRequest(stats = mapOf("없는스탯" to 1.0)))),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Unknown stat")
    }

    @Test
    fun `자리 이름은 대소문자를 가리지 않고 0은 적지 않은 것과 같다`() {
        val training = trainingValidator.validate(
            PositionTrainingRequest(
                mapOf(
                    "c" to SlotTrainingRequest(
                        stats = mapOf(DeckFixtures.STAT to 3.0),
                        skills = listOf(SkillLevelBonus("G_001", 1)),
                    ),
                    "1B" to SlotTrainingRequest(stats = mapOf(DeckFixtures.STAT to 0.0)),
                ),
            ),
        )

        assertThat(training.slots.keys).containsExactly("C")
        assertThat(training.statsFor("C")).containsEntry(DeckFixtures.STAT, 3.0)
        assertThat(training.skillBonusesFor("C")).containsEntry("G_001", 1)
    }

    /** 저장한 적이 없으면 빈 설정을 준다. 화면이 404를 다루지 않아도 된다. */
    @Test
    fun `저장 전에는 빈 설정을 돌려준다`() {
        val stored = HashMap<Long, PositionTraining>()
        val service = PositionTrainingService(
            object : PositionTrainingRepository {
                override fun find(userId: Long) = stored[userId]
                override fun save(userId: Long, training: PositionTraining) =
                    training.also { stored[userId] = it }
            },
            repository,
            setOf(DeckFixtures.STAT),
        )

        assertThat(service.get(1L).slots).isEmpty()

        service.save(
            1L,
            PositionTrainingRequest(mapOf("SP1" to SlotTrainingRequest(stats = mapOf(DeckFixtures.STAT to 2.0)))),
        )

        assertThat(service.get(1L).statsFor("SP1")).containsEntry(DeckFixtures.STAT, 2.0)
    }

    @Test
    fun `로스터 크기는 그대로다`() {
        assertThat(roster().players).hasSize(DeckRoster.ROSTER_SIZE)
    }
}
