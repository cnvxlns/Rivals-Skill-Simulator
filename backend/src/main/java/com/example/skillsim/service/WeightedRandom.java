package com.example.skillsim.service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 가중치 맵을 받아 합계를 기준으로 난수를 굴린 뒤, 비율에 따라 하나의 값을 선택한다.
 */
public final class WeightedRandom {

    private WeightedRandom() {
    }

    public static <T> T pick(Map<T, Double> weights, T defaultValue) {
        if (weights == null || weights.isEmpty()) {
            return defaultValue;
        }

        double total = weights.values().stream()
                .filter(weight -> weight != null && weight > 0)
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0) {
            return defaultValue;
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0;

        for (Map.Entry<T, Double> entry : weights.entrySet()) {
            double weight = sanitize(entry.getValue());
            if (weight <= 0) {
                continue;
            }
            cumulative += weight;
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return defaultValue;
    }

    private static double sanitize(Double value) {
        if (value == null || value <= 0) {
            return 0;
        }
        return value;
    }
}
