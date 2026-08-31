package com.example.skillsim.service

import com.example.skillsim.model.ScoreSkill

/**
 * 스킬 설명의 x·y·z 자리를 실제 수치로 바꾼다.
 *
 * 설명은 게임 원문이라 등급에 따라 달라지는 수치가 x, y, z로 비어 있다. 화면에서 특정
 * 등급을 기준으로 보여줄 때는 그 자리를 채워야 읽을 수 있다.
 *
 * 규칙 두 가지가 데이터에서 확인됐다.
 *
 * - 글자는 효과 행에 순서대로 대응한다. x는 첫째, y는 둘째, z는 셋째 효과다.
 *   (B_006 평정심: "구위 x, 제구 y"의 두 사다리가 서로 다르다)
 * - `x+1`, `3*x` 같은 산술은 사다리에 이미 반영돼 있다. 그래서 글자만 바꾸지 않고
 *   표현 전체를 사다리 값으로 갈아끼운다.
 *   (S_003 타격집중: 설명은 "x+1", 사다리는 2..10이고 S에서 실제 값은 6이다.
 *   글자만 6으로 바꾸면 "6+1"이 되어 채점값과 어긋난다)
 *
 * 대응할 효과가 없으면 원문을 그대로 둔다. 능력치 효과 행이 아예 없는 스킬이 3개 있다.
 */
object SkillDescriptions {

    /**
     * `x`, `x+1`, `3*x` 형태를 한 덩어리로 잡는다.
     *
     * 경계로 라틴 문자와 숫자만 막는다. "MAX"나 "xy" 같은 낱말 속 글자는 이걸로 걸러진다.
     * 한글은 막지 않는다. 원문에 "y증가합니다"처럼 조사가 붙어버린 표기가 4건 있어서
     * 한글까지 막으면 그 스킬들만 치환이 조용히 빠진다.
     */
    private val PLACEHOLDER =
        Regex("""(?<![0-9A-Za-z])(?:\d+\s*\*\s*)?([xyz])(?:\s*\+\s*\d+)?(?![A-Za-z])""")

    /**
     * @param level 사다리에서 읽을 1-based 위치. 채점에 쓴 값을 그대로 넘겨야 화면의
     *              점수·등급과 설명의 수치가 같은 기준을 가리킨다.
     */
    fun resolve(skill: ScoreSkill?, level: Int): String? {
        if (skill == null) {
            return null
        }
        val description = skill.description
        if (description.isBlank()) {
            return description
        }
        val effects = skill.effects
        if (effects.isEmpty()) {
            return description
        }

        return PLACEHOLDER.replace(description) { match ->
            val index = match.groupValues[1][0] - 'x'
            val value = effects.getOrNull(index)?.let { valueTokenAt(it.values, level) }
            // 대응하는 효과가 없거나 사다리가 비었으면 원문을 남긴다. 빈칸보다 낫다.
            value ?: match.value
        }
    }

    /**
     * 사다리에서 해당 위치의 토큰을 문자열 그대로 돌려준다.
     *
     * double로 바꿨다가 다시 찍으면 0.01이 0.010000000000000002가 되거나 6이 6.0이 된다.
     * CSV에 적힌 표기를 그대로 쓰는 편이 화면에도 정확하다.
     */
    private fun valueTokenAt(values: String?, level: Int): String? {
        if (values.isNullOrBlank()) {
            return null
        }
        val tokens = values.split("/")
        val clamped = level.coerceIn(1, tokens.size)
        return tokens[clamped - 1].trim().ifEmpty { null }
    }
}
