package com.example.skillsim.service

import com.example.skillsim.model.ExcelSkillScore

/**
 * 워크북 스킬 점수표에서 이 선수에게 맞는 줄을 고른다.
 *
 * 덱 채점의 스킬 점수는 이 표를 쓴다. 우리 엔진이 내는 값과 다른데(조건 없는 단순 스킬
 * 147건 중 73건이 다르다), 워크북은 상황 확률을 곱하거나 수치를 달리 잡아 뒀다. 덱 총점을
 * 워크북 공식으로 바꾼 이상 스킬 점수도 같은 출처를 써야 총점이 맞는다.
 *
 * 계산기와 점수표 탭은 그대로 우리 엔진을 쓴다. 그쪽은 "왜 이 점수인지"를 스탯·조건·레벨로
 * 풀어 보여 주는 화면이라 표 조회로 바꾸면 설명이 사라진다.
 *
 * 고르는 순서는 셋이다.
 * 1. 사용자가(또는 엑셀 업로드가) 변형을 콕 집었으면 그 줄.
 * 2. 아니면 판정식을 위에서부터 맞춰 보고 처음 걸리는 줄. 기본값(`ALWAYS`)이 맨 뒤에 있다.
 * 3. 아무것도 안 걸리면 null. 호출자가 엔진 값으로 떨어뜨리고 경고를 남긴다.
 */
internal class ExcelSkillScores(
    private val table: Map<Pair<String, Int>, List<ExcelSkillScore>>,
) {

    /** @param explicit 사용자가 변형을 직접 고른 결과인가. 화면이 출처를 그렇게 표시한다. */
    data class Match(val row: ExcelSkillScore, val explicit: Boolean)

    /** 이 스킬·레벨에 변형이 여럿인가. 화면이 옵션 드롭다운을 띄울지 정한다. */
    fun variantsOf(skillId: String, level: Int): List<ExcelSkillScore> =
        table[skillId to level].orEmpty()

    fun lookup(
        skillId: String,
        level: Int,
        option: String?,
        context: SkillScoreSelector.Context,
    ): Match? {
        val rows = table[skillId to level] ?: return null
        if (rows.isEmpty()) return null

        val wanted = option?.trim().orEmpty()
        if (wanted.isNotEmpty()) {
            rows.firstOrNull { it.option == wanted }?.let { return Match(it, explicit = true) }
            // 고른 변형이 표에 없다. 판정으로 넘어가되 호출자가 경고를 남긴다.
        }
        if (rows.size == 1 && rows.first().selector != SkillScoreSelector.MANUAL) {
            return Match(rows.first(), explicit = false)
        }
        val matched = rows.firstOrNull { SkillScoreSelector.matches(it.selector, context) }
        return matched?.let { Match(it, explicit = false) }
    }
}
