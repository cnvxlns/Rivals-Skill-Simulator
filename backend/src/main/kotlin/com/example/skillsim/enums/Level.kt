// 스킬 등급(D~S4)과 비교 편의 메서드를 제공하는 열거형
package com.example.skillsim.enums

enum class Level {
    D,
    C,
    B,
    A,
    S,
    S1,
    S2,
    S3,
    S4,
    ;

    fun isAtLeast(other: Level): Boolean = ordinal >= other.ordinal
}
