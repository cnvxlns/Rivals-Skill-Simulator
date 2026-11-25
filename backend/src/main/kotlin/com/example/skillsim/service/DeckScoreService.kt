package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.dto.ScoreResponse
import com.example.skillsim.enums.RelieverRole
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.repository.ScoreSkillRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 출전 선수 21명을 각자의 조건으로 채점해 종합 점수를 낸다. 후보는 라인업 버프에 참여한다.
 *
 * 선수 하나의 점수는 **능력치 점수 + 스킬 점수**다(워크북의 `J + O = P`). 종합 점수는 그
 * 최종 점수를 파트별로 평균 내 가중치를 매긴 값이다.
 *
 *     선발 평균 × 10 × 0.4  +  계투 평균 × 10 × 0.1  +  타자 평균 × 10 × 0.5
 *
 * 예전에는 26명 점수를 그냥 더했다. 그러면 투수를 6명 쓰는 덱이 4명 쓰는 덱보다 무조건
 * 높게 나오고, 후보의 스킬이 주전과 같은 무게로 들어간다. 평균과 가중치를 쓰면 게임이
 * 실제로 치르는 경기 비중에 가까워진다.
 *
 * 스킬 점수는 워크북 점수표를 쓴다([ExcelSkillScores]). 표에 없는 스킬·레벨만 우리 엔진으로
 * 떨어진다. 계산기와 점수표 탭은 그대로 엔진을 쓴다 — 그쪽은 근거를 펼쳐 보이는 화면이다.
 *
 * @param includeBreakdown 효과 단위 계산 근거까지 담을지. 21명 × 3~4스킬 × 여러 효과면
 *   중첩 객체가 수백 개가 되어 응답이 수백 KB로 불어난다. 기본은 담지 않는다.
 */
@Service
class DeckScoreService private constructor(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val scoreService: ScoreService,
    private val deckData: DeckDataLoader,
    private val statWeights: () -> Map<String, Double>,
) {

    @Autowired
    constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckDataLoader: DeckDataLoader,
        scoreDataLoader: ScoreDataLoader,
    ) : this(scoreSkillRepository, scoreService, deckDataLoader, { scoreDataLoader.statWeights })

    internal constructor(
        scoreSkillRepository: ScoreSkillRepository,
        scoreService: ScoreService,
        deckDataLoader: DeckDataLoader,
        statWeights: Map<String, Double>,
    ) : this(scoreSkillRepository, scoreService, deckDataLoader, { statWeights })

    fun score(
        roster: DeckRoster,
        includeBreakdown: Boolean = false,
        training: PositionTraining = PositionTraining.EMPTY,
    ): DeckScoreResponse {
        val buff = CollectionBuff.of(roster.players.map { it.cardGrade })
        val statResolver = StatResolver(
            deckData.transcendence,
            deckData.enhancement,
            DeckScoreRewardCalculator(deckData.deckScoreRewards),
        )
        val teamBuffs = TeamBuffResolver(deckData.teamBuffSkills, ::findSkill).resolve(roster, training)
        val excelScores = ExcelSkillScores(deckData.excelSkillScores)
        val statScores = StatScoreCalculator(statWeights)

        val relieverCounts = roster.players.filter { DeckRules.isReliever(it.slot) }
            .mapNotNull { it.relieverRole }.groupingBy { it }.eachCount()
        val winCount = relieverCounts[RelieverRole.WIN] ?: 0
        val chaseCount = relieverCounts[RelieverRole.CHASE] ?: 0

        val scored = roster.players.map { player ->
            if (DeckRules.isBench(player.slot)) return@map Scored(player)

            val pitcher = DeckRules.isPitcher(player.slot)
            val collectionBonus = buff.bonusFor(pitcher).toDouble()
            val resolved = statResolver.resolve(player, roster, training, collectionBonus)
            val teamBuff = teamBuffs.forPlayer(player.slot)
            val tacticBonus = if (DeckRules.isReliever(player.slot)) {
                DeckRules.bullpenTacticBonus(player.relieverRole, roster.winTactic, roster.chaseTactic, winCount, chaseCount)
            } else {
                0.0
            }
            val tacticBuff = if (tacticBonus == 0.0) {
                emptyMap()
            } else {
                DeckRules.BULLPEN_TACTIC_STATS.associateWith { tacticBonus }
            }
            val engine = scoreWithEngine(player, resolved, collectionBonus, tacticBuff, training)
            val skills = applyWorkbookScores(player, engine.perSkill, resolved, teamBuff, tacticBuff, excelScores)
            val statScore = statScores.score(addStats(resolved.finalStats, tacticBuff), teamBuff, pitcher)
            Scored(player, resolved, teamBuff, tacticBuff, engine, skills, statScore)
        }

        val parts = DeckPart.entries.map { part ->
            val inPart = scored.filter { DeckRules.partOf(it.player.slot) == part }
            val total = inPart.sumOf { it.finalScore }
            val average = if (inPart.isEmpty()) 0.0 else total / inPart.size
            val weight = DeckRules.PART_WEIGHTS[part] ?: 0.0
            DeckScoreResponse.PartScore(
                part = part.name,
                total = total.roundToScore(),
                playerCount = inPart.size,
                average = average.roundToScore(),
                weight = weight,
                weighted = (average * DeckRules.PART_SCALE * weight).roundToScore(),
            )
        }

        return DeckScoreResponse(
            total = parts.sumOf { it.weighted }.roundToScore(),
            statTotal = scored.sumOf { it.statScore.total }.roundToScore(),
            skillTotal = scored.sumOf { it.skillScore }.roundToScore(),
            parts = parts,
            players = scored
                .map { toPlayerScore(it, includeBreakdown) }
                .sortedByDescending { it.score },
            warnings = scored.flatMap { it.warnings }.distinct(),
            collectionBuff = toBuffInfo(buff),
            teamBuffs = teamBuffs.sources.map { source ->
                DeckScoreResponse.TeamBuffInfo(
                    slot = source.slot,
                    skillId = source.skillId,
                    skillName = source.skillName,
                    level = source.level,
                    scope = source.scope.name,
                    stats = source.stats.map { (stat, amount) ->
                        DeckScoreResponse.TeamBuffInfo.StatAmount(stat, amount)
                    },
                )
            },
        )
    }

    /** 선수 한 명의 채점 결과를 한 덩어리로 들고 다닌다. */
    private data class Scored(
        val player: DeckPlayer,
        val resolved: StatResolver.Resolved? = null,
        val teamBuff: Map<String, Double> = emptyMap(),
        val tacticBuff: Map<String, Double> = emptyMap(),
        val engine: ScoreResponse? = null,
        val skills: List<ScoreResponse.SkillScore> = emptyList(),
        val statScore: StatScoreCalculator.Result = StatScoreCalculator.Result(0.0, emptyList()),
    ) {
        val skillScore: Double get() = skills.sumOf { it.score }
        val finalScore: Double get() = statScore.total + skillScore
        val warnings: List<String>
            get() = engine?.warnings.orEmpty() + resolved?.warnings.orEmpty() + skills.flatMap { it.warnings }
    }

    /**
     * 기존 엔진으로 한 번 채점한다.
     *
     * 워크북 점수를 쓸 때도 이 결과가 필요하다. 스탯별 내역과 계산 근거는 표에 없고,
     * 표에 없는 스킬·레벨은 여기로 떨어지기 때문이다.
     */
    private fun scoreWithEngine(
        player: DeckPlayer,
        resolved: StatResolver.Resolved,
        collectionBonus: Double,
        tacticBuff: Map<String, Double>,
        training: PositionTraining,
    ): ScoreResponse {
        val levelBonuses = training.skillBonusesFor(player.slot)
        val selections = player.skills.map { selection ->
            val skill = findSkill(selection.skillId)
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored deck references unknown skill ${selection.skillId}.",
                )
            ScoreCalculator.Selection(
                withoutTeamBuffEffects(skill),
                selection.level,
                levelBonuses[selection.skillId] ?: 0,
            )
        }
        return scoreService.scoreSelections(
            selections = selections,
            position = player.position,
            cardType = player.cardGrade,
            battingOrder = player.battingOrder,
            pitcherSlot = player.pitcherSlot,
            throwHand = player.throwHand,
            batHand = player.batHand,
            userStats = resolved.userStats,
            baseStats = player.baseStats.ifEmpty { null },
            statBonus = collectionBonus,
            statDeltas = addStats(resolved.statDeltas, tacticBuff),
        )
    }

    /**
     * 팀 버프로 따로 주는 효과행을 뺀 스킬.
     *
     * 빼지 않으면 두 번 센다 — [TeamBuffResolver]가 라인업 전체에 더하는데 보유자에게는
     * 스킬 점수로도 들어가기 때문이다. 워크북 점수표도 같은 이유로 이 부분을 빼 두었다
     * (`(팀버프 X)`·`(투수버프X)` 변형).
     */
    private fun withoutTeamBuffEffects(skill: ScoreSkill): ScoreSkill {
        val buffs = deckData.teamBuffSkills[skill.skillKey] ?: return skill
        val keys = buffs.mapTo(HashSet()) { Triple(it.stat, it.condition, it.rawValues) }
        val kept = skill.effects.filterNot { Triple(it.stat, it.condition, it.values) in keys }
        if (kept.size == skill.effects.size) return skill
        return skill.copy(effects = kept.toMutableList())
    }

    /**
     * 스킬 점수를 워크북 표 값으로 바꾼다.
     *
     * 표에서 줄을 찾지 못하면 엔진 값을 그대로 둔다. 변형이 여럿인데 상황으로 판정할 수
     * 없으면 `optionNeeded`를 세워 화면이 사용자에게 고르게 한다.
     */
    private fun applyWorkbookScores(
        player: DeckPlayer,
        engineSkills: List<ScoreResponse.SkillScore>,
        resolved: StatResolver.Resolved,
        teamBuff: Map<String, Double>,
        tacticBuff: Map<String, Double>,
        excelScores: ExcelSkillScores,
    ): List<ScoreResponse.SkillScore> {
        if (engineSkills.isEmpty()) return engineSkills
        val context = SkillScoreSelector.Context(
            position = player.position,
            cardGrade = player.cardGrade,
            battingOrder = player.battingOrder,
            pitcherSlot = player.pitcherSlot,
            relieverRole = player.relieverRole,
            batHand = player.batHand,
            throwHand = player.throwHand,
            // 미입력 스탯은 판정할 수 없으며, 입력된 스탯에는 팀·전술 버프를 모두 적용한다.
            finalStats = resolved.finalStats.mapValues { (stat, value) ->
                value + (teamBuff[stat] ?: 0.0) + (tacticBuff[stat] ?: 0.0)
            },
            baseStats = player.baseStats,
        )
        return engineSkills.mapIndexed { index, scored ->
            val selection = player.skills.getOrNull(index) ?: return@mapIndexed scored
            val level = levelOf(scored, selection.level)
            val variants = excelScores.variantsOf(selection.skillId, level)
            val match = excelScores.lookup(selection.skillId, level, selection.option, context)
            when {
                match != null -> scored.copy(
                    score = match.row.score,
                    source = ScoreResponse.SOURCE_EXCEL,
                    option = match.row.option.ifEmpty { null },
                )
                variants.isNotEmpty() -> scored.copy(
                    optionNeeded = true,
                    warnings = scored.warnings +
                        "${player.slot}: ${scored.name}의 옵션을 정할 수 없어 계산기 기준으로 채점했습니다.",
                )
                else -> scored
            }
        }
    }

    private fun addStats(stats: Map<String, Double>, bonus: Map<String, Double>): Map<String, Double> =
        if (bonus.isEmpty()) stats else stats.toMutableMap().apply {
            bonus.forEach { (stat, amount) -> merge(stat, amount, Double::plus) }
        }

    /** 포지션 훈련 보너스까지 반영한 레벨. 표도 그 레벨로 찾아야 한다. */
    private fun levelOf(scored: ScoreResponse.SkillScore, level: Int): Int {
        val skill = findSkill(scored.skillId) ?: return level
        return (level + scored.levelBonus).coerceAtMost(SkillRules.maxLevel(skill))
    }

    private fun findSkill(skillId: String): ScoreSkill? = scoreSkillRepository.findBySkillKey(skillId)

    private fun toBuffInfo(buff: CollectionBuff.Result) =
        DeckScoreResponse.CollectionBuffInfo(
            families = buff.families.map {
                DeckScoreResponse.CollectionBuffInfo.FamilyCount(
                    family = it.family.name,
                    count = it.count,
                    batterBonus = it.batterBonus,
                    pitcherBonus = it.pitcherBonus,
                )
            },
            batterBonus = buff.batterBonus,
            pitcherBonus = buff.pitcherBonus,
        )

    private fun toPlayerScore(scored: Scored, includeBreakdown: Boolean) =
        DeckScoreResponse.PlayerScore(
            slot = scored.player.slot,
            position = scored.player.position,
            playerName = scored.player.playerName,
            cardType = scored.player.cardGrade,
            score = scored.finalScore.roundToScore(),
            statScore = scored.statScore.total.roundToScore(),
            skillScore = scored.skillScore.roundToScore(),
            battingOrder = scored.player.battingOrder,
            pitcherSlot = scored.player.pitcherSlot,
            relieverRole = scored.player.relieverRole,
            perSkill = if (includeBreakdown) {
                scored.skills
            } else {
                scored.skills.map { it.copy(breakdown = emptyList()) }
            },
            perStat = scored.engine?.perStat.orEmpty(),
            warnings = scored.warnings,
            finalStats = scored.resolved?.finalStats.orEmpty().map { (stat, value) ->
                DeckScoreResponse.TeamBuffInfo.StatAmount(stat, value.roundToScore())
            },
            teamBuff = scored.teamBuff.map { (stat, value) ->
                DeckScoreResponse.TeamBuffInfo.StatAmount(stat, value)
            },
            tacticBuff = scored.tacticBuff.map { (stat, value) ->
                DeckScoreResponse.TeamBuffInfo.StatAmount(stat, value)
            },
            statSources = scored.resolved?.sources.orEmpty().map {
                DeckScoreResponse.StatSourceInfo(it.stat, it.kind.name, it.amount)
            },
            statEntries = scored.statScore.entries.map {
                DeckScoreResponse.StatScoreEntry(
                    stat = it.stat,
                    value = it.value.roundToScore(),
                    teamBuff = it.teamBuff,
                    weight = it.weight,
                    contribution = it.contribution.roundToScore(),
                )
            },
        )

    /** 합산 과정에서 생기는 부동소수 오차를 자른다. 개별 점수와 같은 소수 둘째 자리 기준이다. */
    private fun Double.roundToScore(): Double = Math.round(this * 100.0) / 100.0
}
