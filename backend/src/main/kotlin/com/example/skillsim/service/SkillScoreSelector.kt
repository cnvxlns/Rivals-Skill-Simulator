package com.example.skillsim.service

import com.example.skillsim.enums.Handedness
import com.example.skillsim.enums.RelieverRole

/**
 * 워크북 스킬 점수표의 옵션 변형을 고르는 판정식.
 *
 * 워크북은 같은 스킬·레벨이라도 조건이 갈리면 다른 줄로 적어 뒀다 — `[S1] 간단한 이닝
 * (변 150-199)`처럼. 그 조건을 선수 상황으로 판정할 수 있으면 우리가 고르고, 판정할 수
 * 없으면(`MANUAL`) 사용자가 고르거나 엔진 값으로 떨어진다.
 *
 * 문법은 좁고 닫혀 있다. `tools/deck_workbook.py`가 만들고 `tools/validate_deck_data.py`가
 * 검사하는 것과 같은 어휘다. 모르는 판정식은 [parse]에서 걸러 절대 참이 되지 않는다 —
 * 조용히 "맞음"으로 떨어지면 점수가 틀린다.
 *
 *     ALWAYS                  언제나 참. 변형이 여럿일 때 맨 뒤의 기본값이다
 *     MANUAL                  판정할 수 없다. 절대 참이 아니다
 *     ORDER=1..2              타순
 *     PSLOT=3..4              투수 슬롯 번호
 *     POS=OF                  포지션
 *     ROLE=RP|CP              선발·중계·마무리
 *     RELIEVER=WIN            중계 하위 역할
 *     BAT=LEFT / THROW=LEFT   투타 방향
 *     GRADE=HOF               카드 등급
 *     STAT(변화)=150..199     최종 능력치
 *     BASESTAT(주루+수비)>=165 카드 고유 능력치
 *
 * `&`로 묶고 `!`로 뒤집는다.
 */
internal object SkillScoreSelector {

    const val ALWAYS = "ALWAYS"
    const val MANUAL = "MANUAL"

    /**
     * 판정에 필요한 선수 상황.
     *
     * 값이 없으면 그 항목을 보는 판정식은 **참이 되지 않는다.** 모르면서 맞다고 하는 것보다
     * 더 넓은 변형이나 엔진 값으로 떨어지는 편이 낫다.
     */
    data class Context(
        val position: String,
        val cardGrade: String,
        val battingOrder: Int? = null,
        val pitcherSlot: Int? = null,
        val relieverRole: RelieverRole? = null,
        val batHand: Handedness? = null,
        val throwHand: Handedness? = null,
        val finalStats: Map<String, Double> = emptyMap(),
        val baseStats: Map<String, Double> = emptyMap(),
    )

    fun matches(selector: String, context: Context): Boolean {
        val trimmed = selector.trim()
        if (trimmed == ALWAYS) return true
        if (trimmed.isEmpty() || trimmed == MANUAL) return false
        return trimmed.split("&").all { term ->
            val negated = term.startsWith("!")
            val atom = if (negated) term.substring(1) else term
            val value = matchesAtom(atom.trim(), context) ?: return false
            value != negated
        }
    }

    /**
     * 문법이 읽히는가. 상황에 맞는지와는 다른 질문이다.
     *
     * [matches]는 "안 맞음"과 "못 읽음"을 똑같이 false로 돌려준다. 못 읽는 판정식은 영영
     * 걸리지 않아 조용히 엔진 값으로 떨어지므로, 데이터가 들어올 때 여기서 걸러야 한다.
     */
    fun isReadable(selector: String): Boolean {
        val trimmed = selector.trim()
        if (trimmed == ALWAYS || trimmed == MANUAL) return true
        if (trimmed.isEmpty()) return false
        return trimmed.split("&").all { term ->
            val atom = (if (term.startsWith("!")) term.substring(1) else term).trim()
            splitAtom(atom)?.first in KNOWN_KEYS
        }
    }

    private val KNOWN_KEYS = setOf(
        "ORDER", "PSLOT", "POS", "ROLE", "RELIEVER", "BAT", "THROW", "GRADE", "STAT", "BASESTAT",
    )

    /** 참·거짓, 또는 판정할 수 없으면 null. */
    private fun matchesAtom(atom: String, context: Context): Boolean? {
        val (key, raw) = splitAtom(atom) ?: return null
        return when (key) {
            "ORDER" -> context.battingOrder?.let { inRange(raw, it) }
            "PSLOT" -> context.pitcherSlot?.let { inRange(raw, it) }
            "POS" -> SkillRules.matchesPosition(raw, context.position)
            "ROLE" -> raw.split("|").any { it == SkillRules.roleForPosition(context.position) }
            "RELIEVER" -> context.relieverRole?.let { it.name == raw }
            "BAT" -> context.batHand?.let { it.name == raw }
            "THROW" -> context.throwHand?.let { it.name == raw }
            "GRADE" -> CardRules.normalizeGrade(context.cardGrade) == raw
            "STAT" -> compareStat(raw, context.finalStats)
            "BASESTAT" -> compareStat(raw, context.baseStats)
            else -> null
        }
    }

    /** `ORDER=1..2` -> ("ORDER", "1..2"), `STAT(변화)=150..199` -> ("STAT", "변화)=150..199"). */
    private fun splitAtom(atom: String): Pair<String, String>? {
        val statMatch = STAT_ATOM.matchEntire(atom)
        if (statMatch != null) {
            val (kind, stat, op, rest) = statMatch.destructured
            return (if (kind.isEmpty()) "STAT" else "BASESTAT") to "$stat\u0000$op$rest"
        }
        val index = atom.indexOf('=')
        if (index <= 0) return null
        return atom.substring(0, index) to atom.substring(index + 1)
    }

    private val STAT_ATOM =
        Regex("""(BASE)?STAT\(([^)]+)\)(=|>=|<)(\d+(?:\.\.\d+)?)""")

    private fun inRange(raw: String, value: Int): Boolean? {
        val parts = raw.split("..")
        val low = parts.first().toIntOrNull() ?: return null
        val high = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else low
        return value in low..high
    }

    /**
     * `변화\u0000=150..199` 꼴을 판정한다.
     *
     * 합산 스탯(`주루+수비`)은 성분을 더한다. `ScoreCalculator`가 비례 효과의 기준 스탯을
     * 다루는 방식과 같다. 성분이 하나라도 없으면 판정하지 않는다.
     */
    private fun compareStat(raw: String, stats: Map<String, Double>): Boolean? {
        val (stat, expression) = raw.split('\u0000', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val value = statValue(stat, stats) ?: return null
        return when {
            expression.startsWith(">=") ->
                expression.removePrefix(">=").toDoubleOrNull()?.let { value >= it }
            expression.startsWith("<") ->
                expression.removePrefix("<").toDoubleOrNull()?.let { value < it }
            expression.startsWith("=") -> {
                val parts = expression.removePrefix("=").split("..")
                val low = parts.first().toDoubleOrNull() ?: return null
                val high = if (parts.size > 1) parts[1].toDoubleOrNull() ?: return null else low
                value in low..high
            }
            else -> null
        }
    }

    private fun statValue(stat: String, stats: Map<String, Double>): Double? {
        val parts = stat.split("+")
        var sum = 0.0
        for (part in parts) {
            sum += stats[part] ?: return null
        }
        return sum
    }
}
