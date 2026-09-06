package com.example.skillsim.service

import com.example.skillsim.dto.DeckScoreResponse
import com.example.skillsim.dto.ScoreResponse
import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.repository.ScoreSkillRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * 덱 26명을 각자의 조건으로 채점해 합산한다.
 *
 * 점수 계산 자체는 기존 [ScoreService.scoreSelections]를 그대로 쓴다. 덱 점수가 단일 선수
 * 점수와 다른 기준을 갖지 않게 하려는 것이며, 이 클래스가 더하는 것은 합산과 소계뿐이다.
 *
 * @param includeBreakdown 효과 단위 계산 근거까지 담을지. 26명 × 3~4스킬 × 여러 효과면
 *   중첩 객체가 수백 개가 되어 응답이 수백 KB로 불어난다. 기본은 담지 않는다.
 */
@Service
class DeckScoreService(
    private val scoreSkillRepository: ScoreSkillRepository,
    private val scoreService: ScoreService,
) {

    fun score(roster: DeckRoster, includeBreakdown: Boolean = false): DeckScoreResponse {
        val scored = roster.players.map { player -> player to scorePlayer(player) }

        val parts = DeckPart.entries.map { part ->
            val inPart = scored.filter { (player, _) -> DeckRules.partOf(player.slot) == part }
            DeckScoreResponse.PartScore(
                part = part.name,
                total = inPart.sumOf { (_, result) -> result.total }.roundToScore(),
                playerCount = inPart.size,
            )
        }

        val players = scored
            .map { (player, result) -> toPlayerScore(player, result, includeBreakdown) }
            .sortedByDescending { it.score }

        return DeckScoreResponse(
            total = scored.sumOf { (_, result) -> result.total }.roundToScore(),
            parts = parts,
            players = players,
            warnings = scored.flatMap { (_, result) -> result.warnings }.distinct(),
        )
    }

    private fun scorePlayer(player: DeckPlayer): ScoreResponse {
        val selections = player.skills.map { selection ->
            val skill = scoreSkillRepository.findBySkillKey(selection.skillId)
                ?: throw ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stored deck references unknown skill ${selection.skillId}.",
                )
            ScoreCalculator.Selection(skill, selection.level)
        }
        return scoreService.scoreSelections(
            selections = selections,
            position = player.position,
            cardType = player.cardType,
            // 후보는 타순이 없다. null이면 ScoreCalculator의 기본 타순으로 채점된다.
            battingOrder = player.battingOrder,
            // 중계 하위 역할은 여기 넘기지 않는다. 조건 게이트가 슬롯 번호만 보기 때문이다.
            pitcherSlot = player.pitcherSlot,
            throwHand = player.throwHand,
            batHand = player.batHand,
            userStats = player.stats,
        )
    }

    private fun toPlayerScore(
        player: DeckPlayer,
        result: ScoreResponse,
        includeBreakdown: Boolean,
    ) = DeckScoreResponse.PlayerScore(
        slot = player.slot,
        position = player.position,
        cardType = player.cardType,
        score = result.total,
        battingOrder = player.battingOrder,
        pitcherSlot = player.pitcherSlot,
        relieverRole = player.relieverRole,
        perSkill = if (includeBreakdown) {
            result.perSkill
        } else {
            result.perSkill.map { it.copy(breakdown = emptyList()) }
        },
        perStat = result.perStat,
        warnings = result.warnings,
    )

    /** 합산 과정에서 생기는 부동소수 오차를 자른다. 개별 점수와 같은 소수 둘째 자리 기준이다. */
    private fun Double.roundToScore(): Double = Math.round(this * 100.0) / 100.0
}
