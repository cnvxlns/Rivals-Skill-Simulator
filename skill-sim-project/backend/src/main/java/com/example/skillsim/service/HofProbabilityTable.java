package com.example.skillsim.service;

import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.Tier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Probability tables for HOF cards with Advanced(Skill Change Premium) and Superior tickets.
 */
final class HofProbabilityTable {

    private static final HofProbabilityTable SKILL_CHANGE = buildSkillChange();
    private static final HofProbabilityTable ADVANCED = buildAdvanced();
    private static final HofProbabilityTable SUPREME_SLOT_ONE = buildSupremeSlotOne();
    private static final HofProbabilityTable SUPREME_OTHER_SLOTS = buildSupremeOtherSlots();

    private final Map<Tier, Map<Level, Double>> gradeWeights;
    private final Map<Tier, Double> tierTotals;

    private HofProbabilityTable(Map<Tier, Map<Level, Double>> gradeWeights) {
        this.gradeWeights = Collections.unmodifiableMap(gradeWeights);
        this.tierTotals = ProbabilityTable.calculateTierTotals(this.gradeWeights);
    }

    static HofProbabilityTable skillChange() {
        return SKILL_CHANGE;
    }

    static HofProbabilityTable advanced() {
        return ADVANCED;
    }

    static HofProbabilityTable supremeSlotOne() {
        return SUPREME_SLOT_ONE;
    }

    static HofProbabilityTable supremeOtherSlots() {
        return SUPREME_OTHER_SLOTS;
    }

    Map<Tier, Double> tierWeights() {
        return tierTotals;
    }

    Map<Level, Double> gradeWeights(Tier tier) {
        return gradeWeights.getOrDefault(tier, Collections.emptyMap());
    }

    private static HofProbabilityTable buildSkillChange() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.IRON, ProbabilityTable.gradeRow(13.986, 10.4895, 6.993, 2.44755, 1.04895));
        table.put(Tier.BRONZE, ProbabilityTable.gradeRow(11.988, 8.991, 5.994, 2.0979, 0.8991));
        table.put(Tier.SILVER, ProbabilityTable.gradeRow(7.992, 5.994, 3.996, 1.3986, 0.5994));
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(5.994, 4.4955, 2.997, 1.04895, 0.44955));
        table.put(Tier.HOF, ProbabilityTable.gradeRow(0.040, 0.030, 0.020, 0.007, 0.003));

        return new HofProbabilityTable(table);
    }

    private static HofProbabilityTable buildAdvanced() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.IRON, ProbabilityTable.gradeRow(13.790, 10.3425, 6.895, 2.41325, 1.03425));
        table.put(Tier.BRONZE, ProbabilityTable.gradeRow(11.820, 8.865, 5.910, 2.0685, 0.8865));
        table.put(Tier.SILVER, ProbabilityTable.gradeRow(7.880, 5.910, 3.940, 1.379, 0.591));
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(5.910, 4.4325, 2.955, 1.03425, 0.44325));
        table.put(Tier.HOF, ProbabilityTable.gradeRow(0.60, 0.45, 0.30, 0.105, 0.045));

        return new HofProbabilityTable(table);
    }

    private static HofProbabilityTable buildSupremeSlotOne() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        // Only GOLD and HOF appear on slot 1
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(36.0, 27.0, 18.0, 6.30, 2.70));
        table.put(Tier.HOF, ProbabilityTable.gradeRow(4.00, 3.00, 2.00, 0.70, 0.30));

        return new HofProbabilityTable(table);
    }

    private static HofProbabilityTable buildSupremeOtherSlots() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        // Iron is excluded for slot 2 and 3
        table.put(Tier.BRONZE, ProbabilityTable.gradeRow(12.60, 9.45, 6.30, 2.205, 0.945));
        table.put(Tier.SILVER, ProbabilityTable.gradeRow(14.40, 10.80, 7.20, 2.520, 1.080));
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(9.00, 6.75, 4.50, 1.575, 0.675));
        table.put(Tier.HOF, ProbabilityTable.gradeRow(4.00, 3.00, 2.00, 0.70, 0.30));

        return new HofProbabilityTable(table);
    }
}
