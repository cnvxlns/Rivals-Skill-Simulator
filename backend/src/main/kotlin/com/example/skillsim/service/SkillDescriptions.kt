package com.example.skillsim.service

import com.example.skillsim.model.ScoreSkill
import org.springframework.core.io.ClassPathResource

/**
 * 스킬 설명의 x·y·z·a·b·c 자리를 실제 수치로 바꾼다.
 *
 * 설명은 게임 원문이라 등급에 따라 달라지는 수치가 글자로 비어 있다. 화면에서 특정 등급을
 * 기준으로 보여 줄 때는 그 자리를 채워야 읽을 수 있다.
 *
 * 글자가 어떤 수치인지는 `skill_placeholders.csv`에 스킬·글자마다 적어 둔다. 예전에는 "x는
 * 첫째 효과 행, y는 둘째 효과 행"으로 짐작했는데, 설명이 y부터 시작하거나(`y`가 첫 효과),
 * 효과 행이 없는 글자(평균 발사각, 확률)가 끼거나, a·b·c까지 쓰는 스킬에서 전부 어긋났다.
 * 표는 글자 자체의 사다리(`x`면 1..9)를 담고, `x+1`·`3*x`·`5*(x+2)` 같은 산술은 여기서 계산한다.
 *
 * 수치를 확인할 원문이 없는 글자는 표에 `-`로 적는다(장타 본능의 평균 발사각 z). 그 자리는 숫자 없이 지운다.
 *
 * 표에 없는 글자는 원문 그대로 둔다. `SkillDescriptionsTest`가 모든 스킬의 모든 등급에서 글자가
 * 하나도 남지 않는지 검사하므로, 새 스킬을 넣으면 표에도 넣어야 테스트가 통과한다.
 */
object SkillDescriptions {

    /**
     * `x`, `{x}`, `x+1`, `3*x`, `0.5*x`, `5*(x+2)`를 한 덩어리로 잡는다. 중괄호는 원문 표기라 함께 지운다.
     *
     * 경계로 라틴 문자·숫자·소수점만 막는다. "MAX"나 "LA" 같은 낱말 속 글자는 이걸로 걸러진다.
     * 한글은 막지 않는다. 원문에 "y증가합니다"처럼 조사가 붙어버린 표기가 있어서다.
     */
    private val PLACEHOLDER = Regex(
        """\{?\s*(?<![0-9A-Za-z.])(?:""" +
            """(\d+(?:\.\d+)?)\s*\*\s*\(\s*([xyzabc])\s*(?:\+\s*(\d+))?\s*\)""" +
            """|(?:(\d+(?:\.\d+)?)\s*\*\s*)?([xyzabc])(?:\s*\+\s*(\d+))?""" +
            """)(?![A-Za-z])\s*}?""",
    )

    private const val TABLE = "skill_placeholders.csv"

    /** 표에 `-`로 적힌 글자. 게임 원문에서 수치를 확인하지 못했다는 뜻이다. */
    private val UNKNOWN = listOf("-")

    /** (스킬 키, 글자) → 글자 사다리. */
    private val ladders: Map<Pair<String, Char>, List<String>> by lazy { loadTable() }

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
        return PLACEHOLDER.replace(description) { match ->
            val groups = match.groupValues
            val (multiplier, letter, addend) = if (groups[2].isNotEmpty()) {
                Triple(groups[1], groups[2], groups[3])
            } else {
                Triple(groups[4], groups[5], groups[6])
            }
            val ladder = ladders[skill.skillKey to letter[0]]
            if (ladder == UNKNOWN) {
                // 수치를 확인할 원문이 없는 자리. 숫자를 지어내지 않고 자리만 지워 "발사각이 증가합니다"로 읽히게 한다.
                return@replace if (match.value.startsWith(' ') && match.value.endsWith(' ')) " " else ""
            }
            val base = ladder?.let { it[level.coerceIn(1, it.size) - 1].toDoubleOrNull() }
            if (base == null) {
                // 표에 없으면 원문을 남긴다. 빈칸보다 낫고, 테스트가 잡아낸다.
                return@replace match.value
            }
            val value = (base + (addend.toDoubleOrNull() ?: 0.0)) * (multiplier.toDoubleOrNull() ?: 1.0)
            // 앞뒤 공백은 표현식 바깥 글자와의 간격이라 살린다.
            match.value.takeWhile { it == ' ' } + format(value) + match.value.takeLastWhile { it == ' ' }
        }
    }

    /** 표에 적힌 (스킬 키, 글자) 전부. 테스트가 쓰지 않는 행을 찾는 데 쓴다. */
    internal fun tableKeys(): Set<Pair<String, Char>> = ladders.keys

    /** 정수면 소수점 없이, 아니면 불필요한 0 없이 적는다(0.5, 1.5). */
    private fun format(value: Double): String {
        val rounded = Math.round(value * 1000) / 1000.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun loadTable(): Map<Pair<String, Char>, List<String>> {
        val resource = ClassPathResource(TABLE)
        if (!resource.exists()) {
            return emptyMap()
        }
        return resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .associate { line ->
                    val (skillKey, letter, values) = line.split(",", limit = 3)
                    (skillKey.trim() to letter.trim()[0]) to values.trim().split("/").map(String::trim)
                }
        }
    }
}
