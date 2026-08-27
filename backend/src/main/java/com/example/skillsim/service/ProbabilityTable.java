package com.example.skillsim.service;

import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Encapsulates tier/grade probability tables per ticket type.
 */
public enum ProbabilityTable {
    NORMAL_PREMIUM(buildNormalPremiumTable()),
    SUPREME(buildSupremeTable()),
    SIGNATURE_BLACK_SUPREME_OTHER(buildSignatureBlackSupremeOtherTable());

    private final Map<Tier, Map<Level, Double>> weights;
    private final Map<Tier, Double> tierTotals;

    ProbabilityTable(Map<Tier, Map<Level, Double>> weights) {
        this.weights = weights;
        this.tierTotals = calculateTierTotals(weights);
    }

    public static ProbabilityTable fromTicket(TicketType ticketType) {
        return switch (ticketType) {
            case SKILL_CHANGE, PREMIUM_SKILL_CHANGE -> NORMAL_PREMIUM;
            case SUPREME_SKILL_CHANGE -> SUPREME;
        };
    }

    public Map<Tier, Double> tierWeights() {
        return tierTotals;
    }

    public Map<Level, Double> gradeWeights(Tier tier) {
        return weights.getOrDefault(tier, Collections.emptyMap());
    }

    private static Map<Tier, Map<Level, Double>> buildNormalPremiumTable() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.IRON, gradeRow(14.0, 10.5, 7.0, 2.45, 1.05));
        table.put(Tier.BRONZE, gradeRow(12.0, 9.0, 6.0, 2.10, 0.90));
        table.put(Tier.SILVER, gradeRow(8.0, 6.0, 4.0, 1.40, 0.60));
        table.put(Tier.GOLD, gradeRow(6.0, 4.5, 3.0, 1.05, 0.45));

        return Collections.unmodifiableMap(table);
    }

    private static Map<Tier, Map<Level, Double>> buildSupremeTable() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.BRONZE, gradeRow(14.0, 10.5, 7.0, 2.45, 1.05));
        table.put(Tier.SILVER, gradeRow(16.0, 12.0, 8.0, 2.80, 1.20));
        table.put(Tier.GOLD, gradeRow(10.0, 7.5, 5.0, 1.75, 0.75));

        return Collections.unmodifiableMap(table);
    }

    private static Map<Tier, Map<Level, Double>> buildSignatureBlackSupremeOtherTable() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.BRONZE, gradeRow(8.0, 6.0, 4.0, 1.40, 0.60));
        table.put(Tier.SILVER, gradeRow(12.0, 9.0, 6.0, 2.10, 0.90));
        table.put(Tier.GOLD, gradeRow(20.0, 15.0, 10.0, 3.50, 1.50));

        return Collections.unmodifiableMap(table);
    }

    static Map<Level, Double> gradeRow(double d, double c, double b, double a, double s) {
        Map<Level, Double> row = new EnumMap<>(Level.class);
        row.put(Level.D, d);
        row.put(Level.C, c);
        row.put(Level.B, b);
        row.put(Level.A, a);
        row.put(Level.S, s);
        return Collections.unmodifiableMap(row);
    }

    static Map<Tier, Double> calculateTierTotals(Map<Tier, Map<Level, Double>> weights) {
        Map<Tier, Double> totals = new EnumMap<>(Tier.class);
        weights.forEach((tier, gradeMap) -> {
            double sum = gradeMap.values().stream()
                    .filter(value -> value != null && value > 0)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            if (sum > 0) {
                totals.put(tier, sum);
            }
        });
        return Collections.unmodifiableMap(totals);
    }
}
