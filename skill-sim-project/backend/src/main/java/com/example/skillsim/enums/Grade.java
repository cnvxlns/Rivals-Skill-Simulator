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
