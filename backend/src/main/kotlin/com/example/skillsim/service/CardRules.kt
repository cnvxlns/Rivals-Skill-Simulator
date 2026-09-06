package com.example.skillsim.service

import java.util.Locale

/**
 * 카드의 두 축 — 등급과 변형.
 *
 * 이전에는 둘을 하나의 `cardType` 문자열로 뭉쳐 `WBC`, `WBC_BLACK` 같은 값을 썼다.
 * 그런데 FA와 WBC는 **서열을 바꾸지 않는 변형**이라 등급과 같은 축에 둘 수 없다.
 * `(fa, wbc) prime`이 모두 프라임과 같은 서열인 것이 그 증거다.
 *
 * 또 하나 구분해야 할 것은 **스킬 티어**다. CSV의 `card_type` 컬럼은 스킬이 속한 풀의
 * 이름이지 카드의 등급이 아니다. 등급이 높을수록 접근 가능한 풀이 넓어지는 관계일 뿐이다.
 * 이 셋을 뭉쳐 두었던 것이 라이브·시즌을 "스킬 풀이 같으니 등급도 같다"고 잘못 놓은 원인이다.
 *
 * 레벨 사다리는 여기 없다. [SkillRules.gradeLadder]는 카드가 아니라 **스킬 풀**의 축이며
 * ("이 풀의 스킬은 몇 단계까지 올라가는가") 호출부가 전부 `skill.cardType`을 넘긴다.
 * 카드 등급이 스킬 레벨의 상한을 따로 두는 규칙은 현재 없으므로 만들지 않는다.
 */
internal object CardRules {

    /**
     * 카드 등급. 낮은 것부터 높은 순이며 이 순서가 곧 서열이다.
     *
     * 서열 근거: `season = live < impact < prime < moment < signature < signature black < hof`.
     * SUPREME_MOMENT는 실제 위치가 확인되지 않아 모먼트와 시그니처 사이에 둔다.
     */
    val GRADES_LOW_TO_HIGH = listOf(
        "SEASON",
        "LIVE",
        "IMPACT",
        "PRIME",
        "MOMENT",
        "SUPREME_MOMENT",
        "SIGNATURE",
        "SIGNATURE_BLACK",
        "HOF",
    )

    /** 변형. 서열에는 영향을 주지 않는다. */
    const val VARIANT_NONE = "NONE"
    const val VARIANT_FA = "FA"
    const val VARIANT_WBC = "WBC"

    val VARIANTS = listOf(VARIANT_NONE, VARIANT_FA, VARIANT_WBC)

    /** FA·WBC 변형을 가질 수 있는 등급. 나머지는 기본형만 존재한다. */
    private val VARIANT_CAPABLE_GRADES = setOf("PRIME", "SIGNATURE", "SIGNATURE_BLACK")

    /**
     * 예전 단일 `cardType` 값을 (등급, 변형)으로 옮긴다.
     *
     * 저장된 덱과 기존 클라이언트가 계속 동작하도록 남겨 둔다. 새 코드는 등급과 변형을
     * 따로 받는다.
     */
    private val LEGACY_CARD_TYPES = mapOf(
        "NORMAL" to ("SIGNATURE" to VARIANT_NONE),
        "SIGNATURE" to ("SIGNATURE" to VARIANT_NONE),
        "BLACK" to ("SIGNATURE_BLACK" to VARIANT_NONE),
        "SIGNATURE_BLACK" to ("SIGNATURE_BLACK" to VARIANT_NONE),
        // 예전의 맨 WBC는 "wbc 시그니처"였다. 확률값이 시그니처와 같았던 것이 근거다.
        "WBC" to ("SIGNATURE" to VARIANT_WBC),
        "WBC_BLACK" to ("SIGNATURE_BLACK" to VARIANT_WBC),
        "WBC_SIGNATURE_BLACK" to ("SIGNATURE_BLACK" to VARIANT_WBC),
        "MOMENT" to ("MOMENT" to VARIANT_NONE),
        "SUPREME_MOMENT" to ("SUPREME_MOMENT" to VARIANT_NONE),
        "HOF" to ("HOF" to VARIANT_NONE),
        "LIVE" to ("LIVE" to VARIANT_NONE),
        "SEASON" to ("SEASON" to VARIANT_NONE),
    )

    fun normalizeGrade(grade: String?): String {
        val normalized = SkillRules.normalizeRequired(grade, "Card grade is required.")
        if (normalized in GRADES_LOW_TO_HIGH) {
            return normalized
        }
        // 예전 이름으로 들어오면 등급 쪽만 꺼내 받아 준다.
        LEGACY_CARD_TYPES[normalized]?.let { return it.first }
        throw IllegalArgumentException("Unsupported card grade: $grade")
    }

    fun normalizeVariant(variant: String?): String {
        val normalized = variant?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (normalized.isEmpty()) {
            return VARIANT_NONE
        }
        if (normalized !in VARIANTS) {
            throw IllegalArgumentException("Unsupported card variant: $variant")
        }
        return normalized
    }

    /** 예전 단일 값을 (등급, 변형)으로 푼다. 모르는 값이면 null. */
    fun fromLegacyCardType(cardType: String?): Pair<String, String>? {
        val normalized = cardType?.trim()?.uppercase(Locale.ROOT).orEmpty()
        return LEGACY_CARD_TYPES[normalized]
    }

    fun validateCombination(grade: String, variant: String) {
        if (variant != VARIANT_NONE && grade !in VARIANT_CAPABLE_GRADES) {
            throw IllegalArgumentException("$grade cards have no $variant variant.")
        }
    }

    /**
     * 이 카드가 고를 수 있는 스킬 풀(= CSV의 card_type 목록).
     *
     * 순서가 곧 스킬 목록에 나오는 순서다. 기본 풀 → 변형 풀 → 등급 전용 풀 순으로,
     * 축을 나누기 전과 같은 배열을 유지한다.
     */
    fun skillPools(grade: String, variant: String): List<String> = buildList {
        // 아이언·브론즈·실버·골드. 모든 카드가 접근한다.
        add("NORMAL")
        if (variant == VARIANT_WBC) {
            add("WBC")
        }
        when (grade) {
            "SIGNATURE_BLACK" -> add("BLACK")
            "MOMENT", "SUPREME_MOMENT" -> add("MOMENT")
            "HOF" -> add("HOF")
        }
    }

    /** 스킬 슬롯 수. 블랙 등급만 4개다. */
    fun slotCount(grade: String): Int =
        if (grade == "SIGNATURE_BLACK") 4 else SkillRules.DEFAULT_SLOT_COUNT
}
