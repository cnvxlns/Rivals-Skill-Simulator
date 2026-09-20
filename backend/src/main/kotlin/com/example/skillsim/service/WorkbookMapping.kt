package com.example.skillsim.service

import com.example.skillsim.enums.Level

/**
 * 워크북 어휘를 우리 어휘로 옮긴다. 상수와 순수 함수만 둔다.
 *
 * 같은 규칙이 `tools/deck_workbook.py`에도 있다. 저쪽은 표를 뽑을 때, 이쪽은 사용자가 올린
 * 워크북을 읽을 때 쓴다. 두 벌이지만 둘 다 같은 워크북을 상대하므로 한쪽만 고치면 곧 드러난다.
 */
internal object WorkbookCards {

    /**
     * 워크북 카드 이름 -> (등급, 변형).
     *
     * 워크북 자체에 띄어쓰기 결함이 있다. 드롭다운은 `FA 시그니처`인데 표 키는 `FA시그니처`라
     * 엑셀 안에서는 VLOOKUP이 #N/A로 떨어진다. 공백을 지우고 맞추면 둘 다 잡힌다.
     */
    private val NAMES: Map<String, Pair<String, String>> = mapOf(
        "명예의전당" to ("HOF" to "NONE"),
        "시그니처블랙" to ("SIGNATURE_BLACK" to "NONE"),
        "WBC시그니처블랙" to ("SIGNATURE_BLACK" to "WBC"),
        "FA시그니처블랙" to ("SIGNATURE_BLACK" to "FA"),
        "시그니처" to ("SIGNATURE" to "NONE"),
        "WBC시그니처" to ("SIGNATURE" to "WBC"),
        "FA시그니처" to ("SIGNATURE" to "FA"),
        "슈프림모먼트" to ("SUPREME_MOMENT" to "NONE"),
        "모먼트" to ("MOMENT" to "NONE"),
        "프라임" to ("PRIME" to "NONE"),
        "WBC프라임" to ("PRIME" to "WBC"),
        "FA프라임" to ("PRIME" to "FA"),
        // 덱 스코어 조건이 참조하지만 드롭다운에는 없는 값. 수식 문자 그대로 살려 둔다.
        "라이브/시즌" to ("LIVE" to "NONE"),
    )

    fun parse(name: String): Pair<String, String>? =
        NAMES[name.replace(Regex("\\s+"), "")]
}

/** 워크북의 스킬 문자열 한 줄. */
internal data class WorkbookSkill(val level: Level, val name: String, val option: String?)

internal object WorkbookSkills {

    private val LABEL = Regex("^\\[(S\\d?)]\\s*(.*)$")
    private val TRAILING_FLAG = Regex("^(.*\\))\\s+([OX])$")

    /**
     * `[S1] 이름 (옵션)`을 쪼갠다.
     *
     * 문법이 하나가 아니다. 괄호가 없는 것(`[S2] WBC 에이스`), 괄호 안에 괄호가 있는 것
     * (`[S0] 라이징 스타 (선발 (O))`), 괄호 뒤에 깃발이 붙은 것(`파워 피쳐(구속>인내) O`),
     * 이름과 괄호가 붙어 있는 것(`[S0]5툴 유격수`)까지 워크북에 실제로 다 있다.
     */
    fun parse(text: String): WorkbookSkill? {
        val match = LABEL.matchEntire(text.trim()) ?: return null
        // 워크북은 S를 S0으로 적는다. 우리 사다리에는 S0이 없다.
        val label = match.groupValues[1].let { if (it == "S0") "S" else it }
        val level = Level.entries.firstOrNull { it.name == label } ?: return null

        var rest = match.groupValues[2].trim()
        var flag: String? = null
        TRAILING_FLAG.matchEntire(rest)?.let {
            rest = it.groupValues[1]
            flag = it.groupValues[2]
        }

        var option: String? = null
        if (rest.endsWith(")")) {
            var depth = 0
            for (i in rest.indices.reversed()) {
                when (rest[i]) {
                    ')' -> depth++
                    '(' -> {
                        depth--
                        if (depth == 0) {
                            option = rest.substring(i + 1, rest.length - 1).trim()
                            rest = rest.substring(0, i).trim()
                        }
                    }
                }
                if (depth == 0 && option != null) break
            }
        }
        flag?.let { option = if (option.isNullOrEmpty()) it else "$option $it" }
        if (rest.isEmpty()) return null
        return WorkbookSkill(level, rest, option?.ifEmpty { null })
    }
}
