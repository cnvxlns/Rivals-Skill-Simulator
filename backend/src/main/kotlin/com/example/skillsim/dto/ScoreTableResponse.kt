package com.example.skillsim.dto

data class ScoreTableResponse(
    /** 티어 그룹. 아이언/브론즈/실버/골드/HOF/모먼트/WBC/블랙 순. */
    val tiers: List<TierGroup>,
    /**
     * 발동 여부를 못박아 볼 수 있는 조건. 요청 포지션의 스킬이 쓰는 조건 중 지금 기준에서
     * 확률이 0과 1 사이인 것만 담는다(타순·포지션처럼 이미 정해진 조건은 나눠 볼 게 없다).
     * 쓰는 스킬이 많은 순서다.
     */
    val conditions: List<ConditionOption> = emptyList(),
) {
    data class ConditionOption(
        val token: String,
        /** 기댓값 채점에 쓰는 발동 확률. */
        val probability: Double,
        /** 요청 포지션의 스킬 중 이 조건을 쓰는 스킬 수. */
        val skillCount: Int,
    )

    data class TierGroup(
        /** 화면 표시에 쓰는 티어 키. i18n 키로도 사용한다. */
        val tier: String,
        /** 이 티어를 채점한 등급. 요청의 [ScoreTableRequest.grades]를 정리한 값이다. */
        val grade: String = "S",
        /** 이 티어에서 고를 수 있는 등급(S~S4 중 이 티어 스킬 사다리에 있는 것). */
        val grades: List<String> = listOf("S"),
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
        /**
         * 한 스킬을 경우별로 나눈 줄의 이름(`주+수합 155 이상 165 미만`, `타순 4~5`). 그 경우에 붙였을 때의
         * 점수라는 뜻이고, 같은 [skillId]가 여러 줄 올 수 있다. 나누지 않은 스킬은 null이다.
         * 나누는 규칙은 [com.example.skillsim.service.ScoreTableVariants]에 있다.
         */
        val variant: String? = null,
        /**
         * 조건을 못박았을 때만 채운다. 못박지 않은 기댓값 점수라 [score]와 다르면
         * 이 스킬이 그 조건의 영향을 받는다는 뜻이다.
         */
        val expectedScore: Double? = null,
    )
}
