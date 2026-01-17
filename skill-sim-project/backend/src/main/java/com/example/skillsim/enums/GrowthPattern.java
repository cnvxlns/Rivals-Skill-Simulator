package com.example.skillsim.enums;

import java.util.Arrays;

/**
 * Growth patterns map a skill's level (index-based: 0=D, 1=C, 2=B, 3=A, 4=S, 5=S1, 6=S2, 7=S3, 8=S4)
 * to a numeric value used to expand logic codes that contain the placeholder "@VAL".
 */
public enum GrowthPattern {
    STAT_LOW(new int[]{2, 3, 4, 5, 7, 8, 9, 10, 11}),
    STAT_HIGH(new int[]{5, 7, 9, 12, 15, 17, 19, 21, 23}),
    FIXED_5(new int[]{5, 5, 5, 5, 5, 5, 5, 5, 5});

    private final int[] values;

    GrowthPattern(int[] values) {
        this.values = values;
    }

    public int valueAt(int levelIndex) {
        if (values.length == 0) {
            return 0;
        }
        if (levelIndex < 0) {
            return values[0];
        }
        if (levelIndex >= values.length) {
            return values[values.length - 1];
        }
        return values[levelIndex];
    }

    public static GrowthPattern fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(pattern -> pattern.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(null);
    }
}
