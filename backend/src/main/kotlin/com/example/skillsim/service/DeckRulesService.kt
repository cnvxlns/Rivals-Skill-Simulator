package com.example.skillsim.service

import com.example.skillsim.config.DeckDataLoader
import com.example.skillsim.dto.DeckRulesResponse
import com.example.skillsim.model.DeckScoreLadder
import com.example.skillsim.model.DeckScoreSide
import org.springframework.stereotype.Service

/** 덱 편집기가 쓰는 규칙을 한 덩어리로 내준다. 계산은 하지 않고 데이터를 모양만 바꾼다. */
@Service
class DeckRulesService(private val deckData: DeckDataLoader) {

    fun rules(): DeckRulesResponse {
        val byTier = deckData.deckScoreRewards.groupBy {
            Triple(it.ladder, it.threshold, it.side)
        }
        return DeckRulesResponse(
            ladders = DeckScoreLadder.entries.map { ladder ->
                DeckRulesResponse.Ladder(
                    ladder = ladder.name,
                    tiers = DeckScoreRules.thresholdsOf(ladder).map { threshold ->
                        DeckRulesResponse.Ladder.Tier(
                            threshold = threshold,
                            decade = DeckScoreRules.isDecadeTier(ladder, threshold),
                            left = effects(byTier, ladder, threshold, DeckScoreSide.LEFT),
                            right = effects(byTier, ladder, threshold, DeckScoreSide.RIGHT),
                        )
                    },
                )
            },
            decadeYears = DeckScoreRules.DECADE_YEARS,
            growth = growthLimits(),
            partWeights = DeckRules.PART_WEIGHTS.mapKeys { it.key.name },
            partScale = DeckRules.PART_SCALE,
        )
    }

    private fun effects(
        byTier: Map<Triple<DeckScoreLadder, Int, DeckScoreSide>, List<com.example.skillsim.model.DeckScoreReward>>,
        ladder: DeckScoreLadder,
        threshold: Int,
        side: DeckScoreSide,
    ): List<DeckRulesResponse.Ladder.Effect> =
        byTier[Triple(ladder, threshold, side)].orEmpty().map {
            DeckRulesResponse.Ladder.Effect(
                target = it.target.group?.name ?: it.target.slots.joinToString("|"),
                stat = it.stat,
                amount = it.amount,
                condition = it.condition.name,
            )
        }

    /**
     * (등급, 변형)별 성장 상한.
     *
     * 표에 아예 없는 등급(라이브·시즌·임팩트)은 내보내지 않는다. 화면이 그 카드에는
     * 드롭다운을 열지 않아야 사용자가 없는 값을 고르지 않는다.
     */
    private fun growthLimits(): List<DeckRulesResponse.GrowthLimit> = buildList {
        for ((track, table) in listOf(
            "TRANSCENDENCE" to deckData.transcendence,
            "ENHANCEMENT" to deckData.enhancement,
        )) {
            table.values
                .groupBy { it.key.cardGrade to it.key.cardVariant }
                .forEach { (card, rows) ->
                    add(
                        DeckRulesResponse.GrowthLimit(
                            track = track,
                            cardGrade = card.first,
                            cardVariant = card.second,
                            maxLevel = rows.minOf { it.maxLevel },
                        ),
                    )
                }
        }
    }
}
