package com.example.skillsim.enums

/**
 * 스킬을 다시 뽑는 아이템.
 *
 * 셋의 차이는 두 갈래다. 하나는 티어 확률([com.example.skillsim.service.RollTables]),
 * 다른 하나는 **결과를 무를 수 있느냐**다. 일반은 뽑은 순간 그대로 적용되고
 * 고급·최고급은 결과를 보고 받거나 무를 수 있다. 기댓값을 읽을 때 이 차이가 중요하다.
 */
enum class TicketType(val revocable: Boolean) {
    /** 스킬 변경권. 결과가 그대로 적용된다. */
    SKILL_CHANGE(revocable = false),

    /** 고급 스킬 변경권. */
    PREMIUM_SKILL_CHANGE(revocable = true),

    /** 최고급 스킬 변경권. */
    SUPREME_SKILL_CHANGE(revocable = true),
}
