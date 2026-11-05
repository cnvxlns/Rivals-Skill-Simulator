package com.example.skillsim.service

import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.ScoreSkill
import com.example.skillsim.model.TeamBuffSkill

/**
 * 라인업 전체를 올려 주는 스킬을 덱에서 찾아 적용한다.
 *
 * 게임 설명문이 "라인업에 등록된 모든 타자/투수", "팀 타자 전체"라고 적은 스킬이다 — 타자·투수
 * 케미스트리, WBC 에이스 둘, 커맨더, 포수 리드, 리더십, 타선지원, 퍼포머트리오(투수),
 * 리그의 지배자 둘, 컴백 플래그. 이 효과는 `score_effects.csv`에 두지 않는다. 계산기와 점수표는 보유자
 * 본인의 효과만 채점하고, 팀 효과는 여기서 라인업에만 준다.
 *
 * 워크북은 같은 것을 팀 설정 드롭다운으로 따로 받는다. 우리는 드롭다운을 두지 않는다 —
 * 덱이 이미 누가 어떤 스킬을 몇 레벨로 갖고 있는지 알기 때문이다. 손으로 고르게 하면 덱과
 * 설정이 어긋나도 아무도 모른다.
 *
 * 주는 쪽은 26명 전원이고, 받는 쪽은 주전 9명(타자 버프)과 투수 12명(투수 버프)이다.
 * 후보는 자기 스킬을 팀에 주지만 자신은 받지 않는다.
 */
internal class TeamBuffResolver(
    private val teamBuffSkills: Map<String, List<TeamBuffSkill>>,
    private val findSkill: (String) -> ScoreSkill?,
) {

    /**
     * @param stats 대상별 능력치 증가. @param sources 누가 무엇을 주는지.
     */
    data class Result(
        val batter: Map<String, Double>,
        val pitcher: Map<String, Double>,
        val sources: List<Source>,
    ) {
        fun forPlayer(slot: String): Map<String, Double> = when {
            DeckRules.isPitcher(slot) -> pitcher
            // 후보는 받지 않는다. 라인업에 등록된 타자가 아니다.
            DeckRules.isLineup(slot) -> batter
            else -> emptyMap()
        }

        companion object {
            val EMPTY = Result(emptyMap(), emptyMap(), emptyList())
        }
    }

    data class Source(
        val slot: String,
        val skillId: String,
        val skillName: String,
        val level: Int,
        val scope: TeamBuffSkill.Scope,
        val stats: Map<String, Double>,
    )

    fun resolve(roster: DeckRoster, training: PositionTraining): Result {
        if (teamBuffSkills.isEmpty()) return Result.EMPTY

        // 같은 스킬을 여럿이 갖고 있으면 가장 높은 레벨 하나만 센다. 설명문이 "동일 스킬과
        // 중복 불가"라고 적었다.
        val best = LinkedHashMap<String, Pair<DeckPlayer, Int>>()
        for (player in roster.players) {
            for (selection in player.skills) {
                val buffs = teamBuffSkills[selection.skillId] ?: continue
                if (!givesFrom(player, buffs)) continue
                val level = effectiveLevel(player, selection.skillId, selection.level, training)
                val current = best[selection.skillId]
                if (current == null || level > current.second) {
                    best[selection.skillId] = player to level
                }
            }
        }

        val batter = LinkedHashMap<String, Double>()
        val pitcher = LinkedHashMap<String, Double>()
        val sources = ArrayList<Source>()
        for ((skillId, holder) in best) {
            val (player, level) = holder
            val buffs = teamBuffSkills.getValue(skillId)
            val given = LinkedHashMap<String, Double>()
            for (buff in buffs) {
                val amount = buff.at(level) * conditionFactor(buff.condition, player)
                if (amount == 0.0) continue
                val target = if (buff.scope == TeamBuffSkill.Scope.BATTER) batter else pitcher
                target.merge(buff.stat, amount, Double::plus)
                given.merge(buff.stat, amount, Double::plus)
            }
            if (given.isEmpty()) continue
            sources += Source(
                slot = player.slot,
                skillId = skillId,
                skillName = findSkill(skillId)?.name ?: skillId,
                level = level,
                scope = buffs.first().scope,
                stats = given,
            )
        }
        return Result(batter, pitcher, sources)
    }

    /** 보유자의 자리와 경기 상황을 본다. 모드 OR·자리 게이트는 개인 효과와 같은 규칙이다. */
    private fun conditionFactor(condition: String, player: DeckPlayer): Double {
        val probabilities = ScoreCalculator.conditionProbabilitiesForPosition(
            position = DeckRules.positionForSlot(player.slot) ?: player.position,
            battingOrder = player.battingOrder,
            pitcherSlot = DeckRules.pitcherSlotNumber(player.slot),
        )
        return ScoreCalculator().conditionProbability(condition, probabilities)
    }

    /**
     * 이 선수가 그 버프를 실제로 주는가.
     *
     * 커맨더와 포수 리드는 설명문이 "포수로 배치하면"이라고 적었다. 포수 자리에 없으면
     * 팀 투수를 올려 주지 않는다.
     */
    private fun givesFrom(player: DeckPlayer, buffs: List<TeamBuffSkill>): Boolean {
        // 자리 게이트를 만족하지 않는 보유자가 최고 레벨 중복 제거에서 다른 보유자를 가리지 않는다.
        if (buffs.none { conditionFactor(it.condition, player) > 0.0 }) return false
        val required = buffs.firstNotNullOfOrNull { it.requiresSlot } ?: return true
        return player.slot == required
    }

    /**
     * 포지션 훈련이 얹어 준 레벨까지 반영한 실효 레벨.
     *
     * 보너스는 선수가 아니라 자리에 붙으므로 그 자리에 선 사람이 받는다.
     * [ScoreCalculator.Selection]이 채점 직전에 더하는 것과 같은 규칙이다.
     */
    private fun effectiveLevel(
        player: DeckPlayer,
        skillId: String,
        level: Int,
        training: PositionTraining,
    ): Int {
        val bonus = training.skillBonusesFor(player.slot)[skillId] ?: 0
        if (bonus == 0) return level
        val skill = findSkill(skillId) ?: return level
        return (level + bonus).coerceAtMost(SkillRules.maxLevel(skill))
    }
}
