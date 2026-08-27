// 스킬 등급(D~S)과 비교 편의 메서드를 제공하는 열거형
package com.example.skillsim.enums;

public enum Level {
    D,
    C,
    B,
    A,
    S,
    S1,
    S2,
    S3,
    S4;

    public boolean isAtLeast(Level other) {
        return this.ordinal() >= other.ordinal();
    }
}
