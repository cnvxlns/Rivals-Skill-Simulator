package com.example.skillsim.service

import com.example.skillsim.model.DeckPlayer
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.PositionTraining
import com.example.skillsim.model.StatGrowth
import com.example.skillsim.model.StatGrowthKey

/**
 * 선수 한 명의 최종 능력치를 낸다.
 *
 * 값을 적는 길이 둘이고 **스탯마다 따로 정해진다.**
 *
 * - **성분**: 그 스탯의 카드 고유 능력치([DeckPlayer.baseStats])가 적혀 있으면 워크북처럼
 *   쌓는다. 기본 + 훈련 + 특훈 + 초월 + 강화 + 포지션 훈련 + 덱 스코어 보상 + 컬렉션 버프.
 * - **직접**: 그 외에는 게임 화면에서 읽은 값([DeckPlayer.stats])을 최종값으로 쓴다. 그 값에는
 *   이미 육성이 다 들어 있으므로 성분을 다시 더하지 않는다(포지션 훈련은 자리를 옮겼을 때의
 *   차이만, 덱 스코어 보상은 아예 더하지 않는다).
 *
 * 선수 단위가 아니라 스탯 단위로 가르는 이유가 있다. 워크북의 성분 칸은 다섯 스탯뿐이라
 * 선수 단위로 갈랐다면 주루·수비 같은 나머지가 0이 된다. 또 기본 능력치 없이 강화 레벨만
 * 적으면 최종값이 10 같은 뜻 없는 수가 된다.
 *
 * 워크북은 성분 합계를 최종 칸(G/H/I)에 연결하지 않는다. 사용자가 보고 손으로 옮겨 적는다.
 * 우리는 배선하고, 두 값이 다르면 경고만 남긴다.
 */
internal class StatResolver(
    private val transcendence: Map<StatGrowthKey, StatGrowth>,
    private val enhancement: Map<StatGrowthKey, StatGrowth>,
    private val rewards: DeckScoreRewardCalculator,
) {

    /** 능력치 점수에 들어가는 스탯. 나머지 스탯은 가중치가 0이라 점수에 기여하지 않는다. */
    companion object {
        val BATTER_STATS = listOf("파워", "정확", "선구")
        val PITCHER_STATS = listOf("변화", "구위")

        /** 성분 합계와 직접 입력값이 이만큼 넘게 어긋나면 알려 준다. */
        private const val MISMATCH_TOLERANCE = 0.5
    }

    data class Source(val stat: String, val kind: Kind, val amount: Double)

    enum class Kind {
        BASE,
        TRAINING,
        SPECIAL_TRAINING,
        TRANSCENDENCE,
        ENHANCEMENT,
        POSITION_TRAINING,
        DECK_SCORE,
        COLLECTION,
        DIRECT,
    }

    data class Resolved(
        /** 엔진에 넘길 보유 스탯. 컬렉션 버프는 빠져 있다(엔진이 statBonus로 따로 더한다). */
        val userStats: Map<String, Double>,
        /** 엔진에 넘길 스탯별 보정. 성분으로 이미 쌓은 스탯은 빠진다. */
        val statDeltas: Map<String, Double>,
        /** 게임 화면에 보일 최종값. 능력치 점수와 판정식이 본다. */
        val finalStats: Map<String, Double>,
        val sources: List<Source>,
        val warnings: List<String>,
    )

    fun resolve(
        player: DeckPlayer,
        roster: DeckRoster,
        training: PositionTraining,
        collectionBonus: Double,
    ): Resolved {
        val statNames = if (DeckRules.isPitcher(player.slot)) PITCHER_STATS else BATTER_STATS
        val componentStats = statNames.filter { player.baseStats[it] != null }

        val trainingStats = training.statsFor(player.slot)
        val rewardStats = rewards.bonusFor(player, roster.deckScoreChoices)

        val sources = ArrayList<Source>()
        val warnings = ArrayList<String>()
        val userStats = LinkedHashMap<String, Double>(player.stats)

        for (stat in componentStats) {
            var sum = 0.0
            for ((kind, amount) in listOf(
                Kind.BASE to player.baseStats[stat],
                Kind.TRAINING to player.trainingStats[stat],
                Kind.SPECIAL_TRAINING to player.specialTrainingStats[stat],
                Kind.TRANSCENDENCE to growth(transcendence, player, stat, player.transcendenceLevel),
                Kind.ENHANCEMENT to growth(enhancement, player, stat, player.enhancementLevel),
                Kind.POSITION_TRAINING to trainingStats[stat],
                Kind.DECK_SCORE to rewardStats[stat],
            )) {
                if (amount == null || amount == 0.0) continue
                sources += Source(stat, kind, amount)
                sum += amount
            }
            val typed = player.stats[stat]
            if (typed != null && kotlin.math.abs(typed - sum) > MISMATCH_TOLERANCE) {
                warnings += "${player.slot}: $stat 성분 합계(${trim(sum)})가 적어 둔 값(${trim(typed)})과 다릅니다."
            }
            userStats[stat] = sum
        }

        // 직접 입력 스탯에만 포지션 훈련 차이를 남긴다. 성분 쪽은 이미 전액을 더했다.
        val deltas = training.statDelta(player.slot, player.statsSlot)
            .filterKeys { it !in componentStats }

        val finalStats = LinkedHashMap<String, Double>()
        for (stat in statNames) {
            val base = userStats[stat]
            if (base == null && stat !in componentStats) {
                // 값을 적지 않은 스탯은 능력치 점수에 넣지 않는다. 워크북의 빈 칸과 같다.
                continue
            }
            val value = (base ?: 0.0) + (deltas[stat] ?: 0.0) + collectionBonus
            finalStats[stat] = value
            if (stat !in componentStats) {
                sources += Source(stat, Kind.DIRECT, base ?: 0.0)
                deltas[stat]?.let { sources += Source(stat, Kind.POSITION_TRAINING, it) }
            }
            if (collectionBonus != 0.0) {
                sources += Source(stat, Kind.COLLECTION, collectionBonus)
            }
        }
        val missing = statNames.filterNot { finalStats.containsKey(it) }
        if (missing.isNotEmpty()) {
            warnings += "${player.slot}: ${missing.joinToString(", ")} 능력치를 적지 않아 0으로 봅니다."
        }

        return Resolved(userStats, deltas, finalStats, sources, warnings)
    }

    /**
     * 카드 성장 표를 찾는다.
     *
     * 워크북 표에는 WBC 변형 셋과 슈프림 모먼트가 없다. 같은 등급의 기본형으로, 슈프림
     * 모먼트는 모먼트로 떨어뜨린다. FA는 따로 있으므로(시그니처와 FA시그니처의 강화가 다르다)
     * 뭉뚱그리지 않고 먼저 정확히 찾는다.
     */
    private fun growth(
        table: Map<StatGrowthKey, StatGrowth>,
        player: DeckPlayer,
        stat: String,
        level: Int?,
    ): Double? {
        if (level == null) return null
        val grade = CardRules.normalizeGrade(player.cardGrade)
        val variant = CardRules.normalizeVariant(player.cardVariant)
        val candidates = buildList {
            add(StatGrowthKey(grade, variant, stat))
            add(StatGrowthKey(grade, "NONE", stat))
            if (grade == "SUPREME_MOMENT") {
                add(StatGrowthKey("MOMENT", "NONE", stat))
            }
        }
        val found = candidates.firstNotNullOfOrNull { table[it] } ?: return null
        return found.at(level).toDouble()
    }

    private fun trim(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
}
