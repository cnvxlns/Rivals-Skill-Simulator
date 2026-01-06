// 스킬 등급(D~S)과 비교 편의 메서드를 제공하는 열거형
package com.example.skillsim.enums;

public enum Grade {
    D,
    C,
    B,
    A,
    S;

    public boolean isAtLeast(Grade other) {
        return this.ordinal() >= other.ordinal();
    }
}
