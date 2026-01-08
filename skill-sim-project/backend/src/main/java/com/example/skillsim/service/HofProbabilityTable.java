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

    private static final HofProbabilityTable ADVANCED = buildAdvanced();
    private static final HofProbabilityTable SUPREME_SLOT_ONE = buildSupremeSlotOne();
    private static final HofProbabilityTable SUPREME_OTHER_SLOTS = buildSupremeOtherSlots();

    private final Map<Tier, Map<Level, Double>> gradeWeights;
    private final Map<Tier, Double> tierTotals;

    private HofProbabilityTable(Map<Tier, Map<Level, Double>> gradeWeights) {
        this.gradeWeights = Collections.unmodifiableMap(gradeWeights);
        this.tierTotals = ProbabilityTable.calculateTierTotals(this.gradeWeights);
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

    private static HofProbabilityTable buildAdvanced() {
        Map<Tier, Map<Level, Double>> table = new EnumMap<>(Tier.class);

        table.put(Tier.IRON, ProbabilityTable.gradeRow(13.85, 10.3875, 6.925, 2.42375, 1.03875));
        table.put(Tier.BRONZE, ProbabilityTable.gradeRow(11.85, 8.8875, 5.925, 2.07375, 0.88875));
        table.put(Tier.SILVER, ProbabilityTable.gradeRow(7.85, 5.8875, 3.925, 1.37375, 0.58875));
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(5.85, 4.3875, 2.925, 1.02375, 0.43875));
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
        table.put(Tier.BRONZE, ProbabilityTable.gradeRow(12.666666, 9.5, 6.333333, 2.216666, 0.95));
        table.put(Tier.SILVER, ProbabilityTable.gradeRow(14.666666, 11.0, 7.333333, 2.566666, 1.10));
        table.put(Tier.GOLD, ProbabilityTable.gradeRow(8.666666, 6.5, 4.333333, 1.516666, 0.65));
        table.put(Tier.HOF, ProbabilityTable.gradeRow(4.00, 3.00, 2.00, 0.70, 0.30));

        return new HofProbabilityTable(table);
    }
}
