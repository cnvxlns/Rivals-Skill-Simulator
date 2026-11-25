package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.DeckSkillRequest
import com.example.skillsim.enums.BullpenTactic
import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.DeckSkillSelection
import com.example.skillsim.repository.InMemoryScoreSkillRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class DeckScoreServiceTest {

    private val repository = DeckFixtures.repository()
    private val csvRepository by lazy { InMemoryScoreSkillRepository().also { ScoreDataLoader(it).run() } }
    private val scoreService = ScoreService(
        repository, ScoreCalculator(), mapOf(DeckFixtures.STAT to 1.0),
    )
    private val deckScoreService = DeckScoreService(repository, scoreService, DeckDataLoader(), mapOf(DeckFixtures.STAT to 1.0))
    private val validator = DeckFixtures.validator(repository)

    private fun roster(): DeckRoster = validator.validate(DeckFixtures.deckRequest())

    @Test
    fun `파트별 가중 소계를 더하면 종합 점수가 된다`() {
        // 예전에는 26명 점수를 그냥 더했다. 이제는 파트 평균에 가중치를 매긴다.
        val result = deckScoreService.score(roster())

        assertThat(result.players).hasSize(DeckRoster.ROSTER_SIZE)
        assertThat(result.parts.sumOf { it.weighted })
            .isCloseTo(result.total, within(0.01))
        assertThat(result.parts.sumOf { it.playerCount }).isEqualTo(DeckRoster.ROSTER_SIZE)
        // 단순 합은 더 이상 종합 점수가 아니다.
        assertThat(result.parts.sumOf { it.total }).isNotEqualTo(result.total)
    }

    @Test
    fun `파트 가중치는 선발 04 계투 01 타자 05이고 후보는 0이다`() {
        val result = deckScoreService.score(roster())
        val byPart = result.parts.associateBy { it.part }

        assertThat(byPart.getValue("ROTATION").weight).isEqualTo(0.4)
        assertThat(byPart.getValue("BULLPEN").weight).isEqualTo(0.1)
        assertThat(byPart.getValue("LINEUP").weight).isEqualTo(0.5)
        assertThat(byPart.getValue("BENCH").weight).isEqualTo(0.0)
        assertThat(byPart.getValue("BENCH").weighted).isEqualTo(0.0)
    }

    @Test
    fun `파트 점수는 평균에 10을 곱한 값이다`() {
        val result = deckScoreService.score(roster())
        val rotation = result.parts.first { it.part == "ROTATION" }

        assertThat(rotation.average)
            .isCloseTo(rotation.total / rotation.playerCount, within(0.01))
        assertThat(rotation.weighted)
            .isCloseTo(rotation.average * 10.0 * 0.4, within(0.01))
    }

    @Test
    fun `후보를 아무리 채워도 종합 점수가 오르지 않는다`() {
        // 가중치 0의 실행 가능한 증명이다. 워크북에는 후보 자리가 아예 없다.
        val base = roster()
        val boosted = base.copy(
            players = base.players.map { player ->
                if (DeckRules.isBench(player.slot)) {
                    player.copy(skills = player.skills.map { it.copy(level = 3) })
                } else {
                    player
                }
            },
        )

        assertThat(deckScoreService.score(boosted).total)
            .isEqualTo(deckScoreService.score(base).total)
    }

    @Test
    fun `선수 점수는 능력치 점수와 스킬 점수의 합이다`() {
        // 워크북의 J + O = P다.
        val players = deckScoreService.score(roster()).players

        assertThat(players).allSatisfy {
            assertThat(it.score).isCloseTo(it.statScore + it.skillScore, within(0.01))
        }
    }

    @Test
    fun `능력치를 적지 않으면 능력치 점수가 0이다`() {
        // 워크북의 빈 칸과 같다. 엔진의 기본값 120은 비례 효과 채점에만 남는다.
        val players = deckScoreService.score(roster()).players

        assertThat(players).allSatisfy { assertThat(it.statScore).isEqualTo(0.0) }
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
    fun `컬렉션 버프가 로스터의 카드 등급에서 계산돼 응답에 담긴다`() {
        // 기본 로스터는 시그니처 26장이라 2·5·8·11·14·17을 모두 넘겨 +6이다.
        val buff = deckScoreService.score(roster()).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(6)
        assertThat(buff.pitcherBonus).isEqualTo(6)
        assertThat(buff.families.first { it.family == "SIGNATURE" }.count)
            .isEqualTo(DeckRoster.ROSTER_SIZE)
    }

    @Test
    fun `계열이 없는 등급만 모으면 버프가 걸리지 않는다`() {
        val impact = validator.validate(
            DeckFixtures.deckRequest { it.copy(cardGrade = "IMPACT") },
        )

        val buff = deckScoreService.score(impact).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(0)
        assertThat(buff.pitcherBonus).isEqualTo(0)
    }

    @Test
    fun `시즌 덱은 타자와 투수의 버프가 다르다`() {
        // 시즌 26장 → 타자는 6·16을, 투수는 10·20을 넘겨 둘 다 +2다.
        val season = validator.validate(
            DeckFixtures.deckRequest { it.copy(cardGrade = "SEASON") },
        )

        val buff = deckScoreService.score(season).collectionBuff

        assertThat(buff.batterBonus).isEqualTo(2)
        assertThat(buff.pitcherBonus).isEqualTo(2)

        // 장수는 덱 전체로 세고 역할은 버프를 받을 대상만 가른다. 타자 14명만 시즌 카드여도
        // 컬렉션은 14장이라 투수 임계값 10도 함께 넘어간다. 시즌 카드를 누가 들고 있는지는
        // 집계에 영향을 주지 않는다.
        val batterOnly = validator.validate(
            DeckFixtures.deckRequest { player ->
                if (DeckRules.isPitcher(player.slot.orEmpty())) player.copy(cardGrade = "IMPACT")
                else player.copy(cardGrade = "SEASON")
            },
        )
        val partial = deckScoreService.score(batterOnly).collectionBuff

        assertThat(partial.families.first { it.family == "SEASON" }.count).isEqualTo(14)
        // 타자 6은 넘고 16은 못 넘어 +1, 투수 10은 넘고 20은 못 넘어 +1.
        assertThat(partial.batterBonus).isEqualTo(1)
        assertThat(partial.pitcherBonus).isEqualTo(1)
    }

    @Test
    fun `전술 기본값은 보직에 능력치 버프를 주지 않는다`() {
        // 워크북 보직 조건이 없는 스킬을 가진 테스트 덱에서 전술 효과만 확인한다.
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
        assertThat(bySlot.getValue("RP2").relieverRole).isEqualTo(RelieverRole.WIN)
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
    fun `후보는 개인 점수와 미입력 경고가 없다`() {
        val result = deckScoreService.score(roster())
        val (bench, active) = result.players.partition { DeckRules.isBench(it.slot) }

        assertThat(bench).hasSize(5)
        assertThat(bench).allSatisfy {
            assertThat(it.score).isZero()
            assertThat(it.statScore).isZero()
            assertThat(it.skillScore).isZero()
            assertThat(it.perSkill).isEmpty()
            assertThat(it.warnings).isEmpty()
        }
        assertThat(result.statTotal).isCloseTo(active.sumOf { it.statScore }, within(0.01))
        assertThat(result.skillTotal).isCloseTo(active.sumOf { it.skillScore }, within(0.01))
    }

    @Test
    fun `스킬 없는 후보도 컬렉션에 포함되고 후보 팀 버프는 주전에게 간다`() {
        val skills = DeckFixtures.repository(listOf(requireNotNull(csvRepository.findBySkillKey("HOF_012"))))
        val weights = mapOf("파워" to 1.1, "정확" to 0.9)
        val scoring = DeckScoreService(
            skills,
            ScoreService(skills, ScoreCalculator(), weights),
            DeckDataLoader().apply { run() },
            weights,
        )
        val validator = DeckFixtures.validator(skills, pools = listOf("NORMAL", "HOF"))
        val request = DeckFixtures.deckRequest {
            if (it.slot?.startsWith("BENCH") == true) {
                it.copy(cardGrade = "HOF", position = null, skills = emptyList())
            } else {
                it
            }
        }
        val base = scoring.score(validator.validate(request))
        val withBuff = scoring.score(validator.validate(request.copy(players = request.players!!.map {
            if (it.slot == "BENCH1") {
                it.copy(skills = listOf(DeckSkillRequest(skillId = "HOF_012", level = 6)))
            } else {
                it
            }
        })))

        assertThat(base.collectionBuff.families.single { it.family == "HOF" }.count).isEqualTo(5)
        assertThat(withBuff.collectionBuff).isEqualTo(base.collectionBuff)
        assertThat(withBuff.teamBuffs.single().slot).isEqualTo("BENCH1")
        assertThat(withBuff.players.single { it.slot == "C" }.statScore).isEqualTo(4.0)
        assertThat(withBuff.players.single { it.slot == "BENCH1" }.score).isZero()
    }

    @Test
    fun `교체 전술은 능력치 미입력 상태의 중계 점수도 올린다`() {
        val weights = mapOf("변화" to 1.15, "구위" to 1.20)
        val scoring = DeckScoreService(repository, scoreService, DeckDataLoader(), weights)
        val base = roster()
        val aggressive = scoring.score(base.copy(
            winTactic = BullpenTactic.AGGRESSIVE,
            chaseTactic = BullpenTactic.AGGRESSIVE,
        )).players.associateBy { it.slot }
        assertThat(aggressive.getValue("RP1").statScore).isEqualTo(4.7)
        assertThat(aggressive.getValue("RP3").statScore).isEqualTo(4.7)
        assertThat(aggressive.getValue("RP5").statScore).isZero()
        assertThat(aggressive.getValue("CP1").statScore).isZero()

        val division = base.copy(
            winTactic = BullpenTactic.DIVISION,
            players = base.players.map {
                if (it.slot == "RP3") it.copy(relieverRole = RelieverRole.WIN) else it
            },
        )
        val winner = scoring.score(division).players.single { it.slot == "RP1" }
        assertThat(winner.statScore).isEqualTo(9.4)
        assertThat(winner.tacticBuff.map { it.stat }).containsExactlyInAnyOrderElementsOf(DeckRules.BULLPEN_TACTIC_STATS)
        assertThat(winner.tacticBuff).allSatisfy { assertThat(it.amount).isEqualTo(4.0) }
        assertThat(winner.tacticBuff.map { it.stat }).doesNotContain("지구력")
    }

    @Test
    fun `교체 전술은 구속 변화 제구 비례 효과와 구간 판정을 바꾸지만 지구력은 유지한다`() {
        val ids = listOf("WBC_013", "G_068", "G_057", "HOF_022", "HOF_033")
        val skills = DeckFixtures.repository(ids.map { requireNotNull(csvRepository.findBySkillKey(it)) })
        val weights = mapOf("변화" to 1.15, "구위" to 1.20)
        val scoring = DeckScoreService(
            skills, ScoreService(skills, ScoreCalculator(), weights), DeckDataLoader().apply { run() }, weights,
        )
        val base = roster()
        val customized = base.copy(players = base.players.map { player ->
            when (player.slot) {
                "RP1" -> player.copy(
                    stats = mapOf("구속" to 118.0, "변화" to 143.0, "제구" to 108.0, "지구력" to 113.0),
                    skills = listOf(
                        DeckSkillSelection("WBC_013", 1), DeckSkillSelection("G_068", 8), DeckSkillSelection("G_057", 5),
                    ),
                )
                "RP2" -> player.copy(
                    stats = mapOf("변화" to 143.0, "지구력" to 113.0),
                    skills = listOf(DeckSkillSelection("HOF_022", 5), DeckSkillSelection("HOF_033", 5)),
                )
                "RP3" -> player.copy(relieverRole = RelieverRole.WIN)
                else -> player
            }
        })
        fun scores(roster: DeckRoster) = scoring.score(roster, includeBreakdown = true)
            .players.flatMap { it.perSkill }.filter { it.skillId in ids }.associateBy { it.skillId }

        val before = scores(customized)
        val after = scores(customized.copy(winTactic = BullpenTactic.DIVISION))
        assertThat(before.getValue("WBC_013").option).isEqualTo("구속 100-124")
        assertThat(after.getValue("WBC_013").option).isEqualTo("구속 125-149")
        assertThat(before.getValue("HOF_022").option).contains("변 100-149")
        assertThat(after.getValue("HOF_022").option).contains("변 150-199")
        assertThat(after.getValue("WBC_013").breakdown.first { it.baseStat == "구속" }.baseValue).isEqualTo(128.0)
        assertThat(after.getValue("G_068").breakdown.first { it.baseStat == "변화+제구" }.baseValue).isEqualTo(271.0)
        for (id in listOf("G_057", "HOF_033")) {
            assertThat(after.getValue(id).breakdown.filter { it.baseStat == "지구력" }.map { it.baseValue })
                .isNotEmpty()
                .isEqualTo(before.getValue(id).breakdown.filter { it.baseStat == "지구력" }.map { it.baseValue })
            assertThat(after.getValue(id).score).isEqualTo(before.getValue(id).score)
        }
    }
}
