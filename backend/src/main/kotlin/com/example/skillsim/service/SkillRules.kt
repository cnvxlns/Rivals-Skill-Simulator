package com.example.skillsim.service

import com.example.skillsim.enums.Level
import com.example.skillsim.model.ScoreSkill
import java.util.Locale

internal object SkillRules {

    const val DEFAULT_SLOT_COUNT = 3

    /**
     * 지원하는 정규화 카드 타입 전체. 새 타입을 더할 때 이 목록이 기준점이다.
     *
     * 카드 타입은 세 곳에 흩어져 있고 한 곳만 고치면 조용히 깨진다.
     * - [normalizeCardType] 누락 시 400
     * - `ScoreService.allowedSkillCardTypes` 누락 시 스킬 0개
     * - `ScoreCalculator`의 상대등급우세 표 누락 시 채점 중 500
     *
     * SkillRulesTest가 이 목록을 훑어 세 곳을 모두 검사한다.
     */
    val CARD_TYPES = listOf(
        "NORMAL", "BLACK", "WBC", "WBC_BLACK", "MOMENT", "SUPREME_MOMENT", "HOF", "LIVE", "SEASON",
    )

    private val BATTER_POSITIONS =
        setOf("BATTER", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH", "IF", "OF")
    private val INFIELD_POSITIONS = setOf("1B", "2B", "3B", "SS")
    private val PITCHER_POSITIONS = setOf("PITCHER", "SP", "RP", "CP")
    private val OUTFIELD_POSITIONS = setOf("OF", "LF", "CF", "RF")

    fun normalizeCardType(cardType: String?): String =
        when (val normalized = normalizeRequired(cardType, "Card type is required.")) {
            "SIGNATURE", "NORMAL" -> "NORMAL"
            "SIGNATURE_BLACK", "BLACK" -> "BLACK"
            "WBC_SIGNATURE_BLACK", "WBC_BLACK" -> "WBC_BLACK"
            "WBC", "MOMENT", "HOF", "SUPREME_MOMENT", "LIVE", "SEASON" -> normalized
            else -> throw IllegalArgumentException("Unsupported card type: $cardType")
        }

    /**
     * 스킬 슬롯 수. 블랙 계열만 4개이고 나머지는 3개다.
     *
     * 라이브/시즌은 전용 스킬이 없고 아이언·브론즈·실버·골드만 가지므로 기본값 3을 그대로 쓴다.
     * 게임 규칙이 다르면 아래 조건 한 줄만 고치면 된다.
     */
    fun slotCount(cardType: String?): Int =
        if (normalizeCardType(cardType) in setOf("BLACK", "WBC_BLACK")) 4 else DEFAULT_SLOT_COUNT

    /**
     * 카드 타입별 등급 사다리.
     *
     * NORMAL과 라이브/시즌이 else로 떨어져 D~S4 전체를 쓴다. 셋 다 스킬 풀이 같으므로
     * (아이언·브론즈·실버·골드) 사다리도 같은 것이 맞다. 라이브/시즌만 달라지면 분기를 하나 더한다.
     */
    fun gradeLadder(cardType: String?): List<Level> =
        when (normalizeCardType(cardType)) {
            "MOMENT", "SUPREME_MOMENT" -> listOf(Level.S)
            "WBC", "WBC_BLACK" -> listOf(Level.S, Level.S1, Level.S2)
            "BLACK" -> listOf(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2)
            "HOF" -> listOf(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1)
            else -> listOf(
                Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4,
            )
        }

    fun gradeLabels(cardType: String?, maxLevel: Int): List<String> {
        val ladder = gradeLadder(cardType)
        val count = maxLevel.coerceIn(1, ladder.size)
        return ladder.take(count).map { it.name }
    }

    fun levelIndex(level: Level?, cardType: String?): Int {
        val ladder = gradeLadder(cardType)
        val idx = ladder.indexOf(level)
        if (idx >= 0) {
            return idx + 1
        }
        return ((level?.ordinal ?: 0) + 1).coerceIn(1, ladder.size)
    }

    fun maxLevel(skill: ScoreSkill): Int {
        val valueCount = skill.effects
            .maxOfOrNull { effect ->
                if (effect.values.isBlank()) 1 else effect.values.split("/").size
            }
            ?: 1
        return valueCount.coerceIn(1, gradeLadder(skill.cardType).size)
    }

    fun matchesPosition(skillPosition: String?, requestedPosition: String?): Boolean {
        val skill = normalize(skillPosition)
        val requested = normalize(requestedPosition)
        if (skill.isEmpty() || requested.isEmpty()) {
            return false
        }
        if (skill == requested) {
            return true
        }
        val skillTokens = splitPositionTokens(skill)
        return when {
            requested == "BATTER" -> skillTokens.any { it in BATTER_POSITIONS }
            requested == "PITCHER" -> skillTokens.any { it in PITCHER_POSITIONS }
            requested == "OF" -> skillTokens.any { it in OUTFIELD_POSITIONS }
            requested == "IF" -> skillTokens.any { it == "IF" || it in INFIELD_POSITIONS }
            requested in setOf("LF", "CF", "RF") && "OF" in skillTokens -> true
            requested in INFIELD_POSITIONS && "IF" in skillTokens -> true
            requested in PITCHER_POSITIONS && "PITCHER" in skillTokens -> true
            requested in BATTER_POSITIONS && "BATTER" in skillTokens -> true
            else -> requested in skillTokens
        }
    }

    fun normalizePosition(position: String?): String = normalize(position)

    fun normalizeRequired(value: String?, message: String): String {
        val normalized = normalize(value)
        if (normalized.isEmpty()) {
            throw IllegalArgumentException(message)
        }
        return normalized
    }

    fun roleForPosition(position: String?): String =
        when (normalize(position)) {
            "CP" -> "CP"
            "RP" -> "RP"
            "SP", "PITCHER" -> "SP"
            else -> "BATTER"
        }

    private fun splitPositionTokens(position: String): Set<String> =
        position.replace("CR", "CF").replace(".", ",")
            .split(Regex("""[,/|\s]+"""))
            .filter { it.isNotBlank() }
            .map { it.trim() }
            .toSet()

    private fun normalize(value: String?): String = value?.trim()?.uppercase(Locale.ROOT) ?: ""
}
