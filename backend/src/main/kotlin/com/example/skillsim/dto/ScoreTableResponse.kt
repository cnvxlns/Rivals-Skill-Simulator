package com.example.skillsim.dto

data class ScoreTableResponse(
    /** 티어 그룹. 아이언/브론즈/실버/골드/HOF/모먼트/WBC/블랙 순. */
    val tiers: List<TierGroup>,
) {
    data class TierGroup(
        /** 화면 표시에 쓰는 티어 키. i18n 키로도 사용한다. */
        val tier: String,
        /** 이 티어에서 요청 포지션에 해당하는 스킬 수. entries는 상위 일부만 담을 수 있다. */
        val totalCount: Int,
        val entries: List<Entry>,
    )

    data class Entry(
        val skillId: String,
        val name: String,
        val description: String?,
        /** 설명의 x·y·z를 appliedGrade 기준 실제 수치로 바꾼 것. 치환할 수 없으면 원문과 같다. */
        val resolvedDescription: String?,
        val score: Double,
        /** 실제 채점에 쓰인 등급. S가 없는 스킬은 자기 최대 등급으로 내려간다. */
        val appliedGrade: String,
    )
}
