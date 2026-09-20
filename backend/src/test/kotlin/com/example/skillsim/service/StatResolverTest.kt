package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.SlotTraining
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

/**
 * 최종 능력치를 어떻게 만드는가.
 *
 * 값을 적는 길이 둘이고 **스탯마다 따로** 정해진다. 그 갈림이 이 클래스의 전부다.
 */
class StatResolverTest {

    private val deckData = DeckDataLoader().apply { run() }

    private fun resolver(rewards: DeckScoreRewardCalculator = DeckScoreRewardCalculator(emptyList())) =
        StatResolver(deckData.transcendence, deckData.enhancement, rewards)

    private fun player(
        slot: String = "C",
        grade: String = "SIGNATURE",
        variant: String = "NONE",
        stats: Map<String, Double> = emptyMap(),
        baseStats: Map<String, Double> = emptyMap(),
        trainingStats: Map<String, Double> = emptyMap(),
        specialTrainingStats: Map<String, Double> = emptyMap(),
        transcendence: Int? = null,
        enhancement: Int? = null,
        statsSlot: String? = null,
    ) = DeckPlayer(
        slot = slot,
        position = DeckRules.positionForSlot(slot) ?: "C",
        cardGrade = grade,
        cardVariant = variant,
        skills = emptyList(),
        battingOrder = if (DeckRules.isLineup(slot)) 1 else null,
        pitcherSlot = DeckRules.pitcherSlotNumber(slot),
        stats = stats,
        statsSlot = statsSlot,
        baseStats = baseStats,
        trainingStats = trainingStats,
        specialTrainingStats = specialTrainingStats,
        transcendenceLevel = transcendence,
        enhancementLevel = enhancement,
    )

    private fun roster(player: DeckPlayer) =
        DeckRoster(starterCount = 5, closerCount = 1, players = listOf(player))

    @Test
    fun `기본 능력치를 적은 스탯은 성분을 쌓는다`() {
        val subject = player(
            baseStats = mapOf("파워" to 65.0),
            trainingStats = mapOf("파워" to 15.0),
            specialTrainingStats = mapOf("파워" to 3.0),
        )

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        assertThat(resolved.finalStats.getValue("파워")).isEqualTo(83.0)
        assertThat(resolved.sources.filter { it.stat == "파워" }.map { it.kind })
            .containsExactly(
                StatResolver.Kind.BASE,
                StatResolver.Kind.TRAINING,
                StatResolver.Kind.SPECIAL_TRAINING,
            )
    }

    @Test
    fun `기본 능력치가 없는 스탯은 적어 둔 값을 그대로 쓴다`() {
        // 성분 칸은 다섯 스탯뿐이다. 선수 단위로 갈랐다면 나머지가 0이 된다.
        val subject = player(
            stats = mapOf("파워" to 120.0, "정확" to 110.0),
            baseStats = mapOf("파워" to 65.0),
            trainingStats = mapOf("파워" to 15.0),
        )

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        assertThat(resolved.finalStats.getValue("파워")).isEqualTo(80.0)
        assertThat(resolved.finalStats.getValue("정확")).isEqualTo(110.0)
    }

    @Test
    fun `성분 합계가 적어 둔 값과 다르면 경고만 남긴다`() {
        // 워크북은 성분 합계를 최종 칸에 연결하지 않는다. 사용자가 손으로 옮겨 적는다.
        val subject = player(
            stats = mapOf("파워" to 999.0),
            baseStats = mapOf("파워" to 65.0),
        )

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        assertThat(resolved.finalStats.getValue("파워")).isEqualTo(65.0)
        assertThat(resolved.warnings).anySatisfy { assertThat(it).contains("성분 합계") }
    }

    @Test
    fun `적지 않은 스탯은 0으로 보고 알려 준다`() {
        val subject = player()

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        assertThat(resolved.finalStats).isEmpty()
        assertThat(resolved.warnings).anySatisfy { assertThat(it).contains("적지 않아") }
    }

    @Test
    fun `초월과 강화는 카드별 표에서 온다`() {
        val subject = player(
            grade = "HOF",
            baseStats = mapOf("파워" to 0.0),
            transcendence = 15,
            enhancement = 20,
        )

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        // 명예의 전당 파워: 초월 15레벨 누적 6, 강화 20레벨 누적 25.
        assertThat(resolved.finalStats.getValue("파워")).isEqualTo(31.0)
    }

    @Test
    fun `FA 변형은 기본형과 다른 표를 쓴다`() {
        // 강화 파워: 시그니처 20레벨 25, FA시그니처 20레벨 21. 뭉뚱그리면 4가 틀린다.
        val plain = player(baseStats = mapOf("파워" to 0.0), enhancement = 20)
        val fa = player(variant = "FA", baseStats = mapOf("파워" to 0.0), enhancement = 20)

        assertThat(resolver().resolve(plain, roster(plain), PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 25.0)
        assertThat(resolver().resolve(fa, roster(fa), PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 21.0)
    }

    @Test
    fun `WBC 변형과 슈프림 모먼트는 표가 없어 기본형으로 떨어진다`() {
        val wbc = player(variant = "WBC", baseStats = mapOf("파워" to 0.0), enhancement = 20)
        val supreme = player(grade = "SUPREME_MOMENT", baseStats = mapOf("파워" to 0.0), enhancement = 20)

        assertThat(resolver().resolve(wbc, roster(wbc), PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 25.0)
        // 모먼트 파워 20레벨은 20이다.
        assertThat(resolver().resolve(supreme, roster(supreme), PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 20.0)
    }

    @Test
    fun `표가 아예 없는 등급은 0이다`() {
        // 라이브·시즌·임팩트는 워크북 표에 없다. 근거 없는 값을 지어내지 않는다.
        val live = player(grade = "LIVE", baseStats = mapOf("파워" to 10.0), enhancement = 20)

        assertThat(resolver().resolve(live, roster(live), PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 10.0)
    }

    @Test
    fun `사다리를 넘는 레벨은 마지막 칸에서 멈춘다`() {
        // 시그니처의 초월은 9에서 끝난다. 15를 넣어도 9의 값이어야 한다.
        val nine = player(baseStats = mapOf("파워" to 0.0), transcendence = 9)
        val fifteen = player(baseStats = mapOf("파워" to 0.0), transcendence = 15)

        assertThat(resolver().resolve(fifteen, roster(fifteen), PositionTraining.EMPTY, 0.0).finalStats)
            .isEqualTo(resolver().resolve(nine, roster(nine), PositionTraining.EMPTY, 0.0).finalStats)
    }

    @Test
    fun `성분 모드는 포지션 훈련 전액을 더하고 직접 모드는 차이만 더한다`() {
        val training = PositionTraining(
            slots = mapOf(
                "C" to SlotTraining(stats = mapOf("파워" to 5.0)),
                "1B" to SlotTraining(stats = mapOf("파워" to 2.0)),
            ),
        )

        val component = player(baseStats = mapOf("파워" to 60.0))
        val resolvedComponent = resolver().resolve(component, roster(component), training, 0.0)
        assertThat(resolvedComponent.finalStats.getValue("파워")).isEqualTo(65.0)
        assertThat(resolvedComponent.statDeltas).doesNotContainKey("파워")

        // 직접 입력값에는 적을 당시 자리의 포훈이 이미 들어 있다. 옮긴 차이만 보정한다.
        val direct = player(stats = mapOf("파워" to 100.0), statsSlot = "1B")
        val resolvedDirect = resolver().resolve(direct, roster(direct), training, 0.0)
        assertThat(resolvedDirect.finalStats.getValue("파워")).isEqualTo(103.0)
    }

    @Test
    fun `덱 스코어 보상은 성분 모드에만 들어간다`() {
        val rewards = DeckScoreRewardCalculator(deckData.deckScoreRewards)
        val choices = listOf(
            com.example.skillsim.model.DeckScoreChoice(
                com.example.skillsim.model.DeckScoreLadder.TEAM,
                200,
                com.example.skillsim.model.DeckScoreSide.LEFT,
            ),
        )

        val component = player(baseStats = mapOf("파워" to 60.0))
        val componentRoster = roster(component).copy(deckScoreChoices = choices)
        // 팀 200 좌는 전 스탯 +3이다.
        assertThat(resolver(rewards).resolve(component, componentRoster, PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 63.0)

        val direct = player(stats = mapOf("파워" to 60.0))
        val directRoster = roster(direct).copy(deckScoreChoices = choices)
        assertThat(resolver(rewards).resolve(direct, directRoster, PositionTraining.EMPTY, 0.0).finalStats)
            .containsEntry("파워", 60.0)
    }

    @Test
    fun `컬렉션 버프는 두 모드 모두에 더한다`() {
        val component = player(baseStats = mapOf("파워" to 60.0))
        val direct = player(stats = mapOf("파워" to 60.0))

        assertThat(resolver().resolve(component, roster(component), PositionTraining.EMPTY, 6.0).finalStats)
            .containsEntry("파워", 66.0)
        assertThat(resolver().resolve(direct, roster(direct), PositionTraining.EMPTY, 6.0).finalStats)
            .containsEntry("파워", 66.0)
    }

    @Test
    fun `투수는 변화와 구위만 본다`() {
        val subject = player(slot = "SP1", baseStats = mapOf("변화" to 70.0, "구위" to 79.0))

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 0.0)

        assertThat(resolved.finalStats.keys).containsExactlyInAnyOrder("변화", "구위")
    }

    @Test
    fun `엔진에 넘기는 값에는 컬렉션 버프가 빠져 있다`() {
        // 엔진이 statBonus로 따로 더한다. 여기서 더하면 비례 효과가 두 번 센다.
        val subject = player(baseStats = mapOf("파워" to 60.0))

        val resolved = resolver().resolve(subject, roster(subject), PositionTraining.EMPTY, 6.0)

        assertThat(resolved.userStats.getValue("파워")).isEqualTo(60.0)
        assertThat(resolved.finalStats.getValue("파워")).isCloseTo(66.0, within(0.001))
    }
}
