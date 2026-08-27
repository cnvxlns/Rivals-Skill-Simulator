package com.example.skillsim.service;

import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.Tier;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HofProbabilityTableTest {

    @Test
    void skillChangeTableMatchesDocumentedWeights() {
        HofProbabilityTable table = HofProbabilityTable.skillChange();

        assertThat(table.tierWeights())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        Tier.IRON, 34.965,
                        Tier.BRONZE, 29.970,
                        Tier.SILVER, 19.980,
                        Tier.GOLD, 14.985,
                        Tier.HOF, 0.100
                ));
        assertThat(table.gradeWeights(Tier.IRON))
                .containsExactlyInAnyOrderEntriesOf(row(13.986, 10.4895, 6.993, 2.44755, 1.04895));
        assertThat(table.gradeWeights(Tier.BRONZE))
                .containsExactlyInAnyOrderEntriesOf(row(11.988, 8.991, 5.994, 2.0979, 0.8991));
        assertThat(table.gradeWeights(Tier.SILVER))
                .containsExactlyInAnyOrderEntriesOf(row(7.992, 5.994, 3.996, 1.3986, 0.5994));
        assertThat(table.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(5.994, 4.4955, 2.997, 1.04895, 0.44955));
        assertThat(table.gradeWeights(Tier.HOF))
                .containsExactlyInAnyOrderEntriesOf(row(0.040, 0.030, 0.020, 0.007, 0.003));
    }

    @Test
    void advancedTableMatchesDocumentedWeights() {
        HofProbabilityTable table = HofProbabilityTable.advanced();

        assertThat(table.tierWeights())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        Tier.IRON, 34.475,
                        Tier.BRONZE, 29.550,
                        Tier.SILVER, 19.700,
                        Tier.GOLD, 14.775,
                        Tier.HOF, 1.5
                ));
        assertThat(table.gradeWeights(Tier.IRON))
                .containsExactlyInAnyOrderEntriesOf(row(13.790, 10.3425, 6.895, 2.41325, 1.03425));
        assertThat(table.gradeWeights(Tier.BRONZE))
                .containsExactlyInAnyOrderEntriesOf(row(11.820, 8.865, 5.910, 2.0685, 0.8865));
        assertThat(table.gradeWeights(Tier.SILVER))
                .containsExactlyInAnyOrderEntriesOf(row(7.880, 5.910, 3.940, 1.379, 0.591));
        assertThat(table.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(5.910, 4.4325, 2.955, 1.03425, 0.44325));
        assertThat(table.gradeWeights(Tier.HOF))
                .containsExactlyInAnyOrderEntriesOf(row(0.60, 0.45, 0.30, 0.105, 0.045));
    }

    @Test
    void supremeSlotOneRestrictsToGoldAndHof() {
        HofProbabilityTable table = HofProbabilityTable.supremeSlotOne();

        assertThat(table.tierWeights())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        Tier.GOLD, 90.0,
                        Tier.HOF, 10.0
                ));
        assertThat(table.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(36.0, 27.0, 18.0, 6.30, 2.70));
        assertThat(table.gradeWeights(Tier.HOF))
                .containsExactlyInAnyOrderEntriesOf(row(4.00, 3.00, 2.00, 0.70, 0.30));
        assertThat(table.gradeWeights(Tier.IRON)).isEmpty();
        assertThat(table.gradeWeights(Tier.BRONZE)).isEmpty();
        assertThat(table.gradeWeights(Tier.SILVER)).isEmpty();
    }

    @Test
    void supremeOtherSlotsExcludeIronAndMatchDocumentedRows() {
        HofProbabilityTable table = HofProbabilityTable.supremeOtherSlots();

        assertThat(table.tierWeights()).doesNotContainKey(Tier.IRON);
        assertThat(table.tierWeights().get(Tier.BRONZE)).isCloseTo(31.5, within(0.000001));
        assertThat(table.tierWeights().get(Tier.SILVER)).isCloseTo(36.0, within(0.000001));
        assertThat(table.tierWeights().get(Tier.GOLD)).isCloseTo(22.5, within(0.000001));
        assertThat(table.tierWeights().get(Tier.HOF)).isEqualTo(10.0);
        assertThat(table.gradeWeights(Tier.BRONZE))
                .containsExactlyInAnyOrderEntriesOf(row(12.60, 9.45, 6.30, 2.205, 0.945));
        assertThat(table.gradeWeights(Tier.SILVER))
                .containsExactlyInAnyOrderEntriesOf(row(14.40, 10.80, 7.20, 2.520, 1.080));
        assertThat(table.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(9.00, 6.75, 4.50, 1.575, 0.675));
        assertThat(table.gradeWeights(Tier.HOF))
                .containsExactlyInAnyOrderEntriesOf(row(4.00, 3.00, 2.00, 0.70, 0.30));
    }

    private static Map<Level, Double> row(double d, double c, double b, double a, double s) {
        return Map.of(
                Level.D, d,
                Level.C, c,
                Level.B, b,
                Level.A, a,
                Level.S, s
        );
    }
}
