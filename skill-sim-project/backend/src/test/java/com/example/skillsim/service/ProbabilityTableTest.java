package com.example.skillsim.service;

import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbabilityTableTest {

    @Test
    void normalAndPremiumTicketsUseLegacyNormalPremiumWeights() {
        assertThat(ProbabilityTable.fromTicket(TicketType.SKILL_CHANGE))
                .isSameAs(ProbabilityTable.NORMAL_PREMIUM);
        assertThat(ProbabilityTable.fromTicket(TicketType.PREMIUM_SKILL_CHANGE))
                .isSameAs(ProbabilityTable.NORMAL_PREMIUM);

        assertThat(ProbabilityTable.NORMAL_PREMIUM.tierWeights())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        Tier.IRON, 35.0,
                        Tier.BRONZE, 30.0,
                        Tier.SILVER, 20.0,
                        Tier.GOLD, 15.0
                ));
        assertThat(ProbabilityTable.NORMAL_PREMIUM.gradeWeights(Tier.IRON))
                .containsExactlyInAnyOrderEntriesOf(row(14.0, 10.5, 7.0, 2.45, 1.05));
        assertThat(ProbabilityTable.NORMAL_PREMIUM.gradeWeights(Tier.BRONZE))
                .containsExactlyInAnyOrderEntriesOf(row(12.0, 9.0, 6.0, 2.10, 0.90));
        assertThat(ProbabilityTable.NORMAL_PREMIUM.gradeWeights(Tier.SILVER))
                .containsExactlyInAnyOrderEntriesOf(row(8.0, 6.0, 4.0, 1.40, 0.60));
        assertThat(ProbabilityTable.NORMAL_PREMIUM.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(6.0, 4.5, 3.0, 1.05, 0.45));
    }

    @Test
    void supremeTicketUsesLegacySupremeWeights() {
        assertThat(ProbabilityTable.fromTicket(TicketType.SUPREME_SKILL_CHANGE))
                .isSameAs(ProbabilityTable.SUPREME);

        assertThat(ProbabilityTable.SUPREME.tierWeights())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        Tier.BRONZE, 35.0,
                        Tier.SILVER, 40.0,
                        Tier.GOLD, 25.0
                ));
        assertThat(ProbabilityTable.SUPREME.gradeWeights(Tier.BRONZE))
                .containsExactlyInAnyOrderEntriesOf(row(14.0, 10.5, 7.0, 2.45, 1.05));
        assertThat(ProbabilityTable.SUPREME.gradeWeights(Tier.SILVER))
                .containsExactlyInAnyOrderEntriesOf(row(16.0, 12.0, 8.0, 2.80, 1.20));
        assertThat(ProbabilityTable.SUPREME.gradeWeights(Tier.GOLD))
                .containsExactlyInAnyOrderEntriesOf(row(10.0, 7.5, 5.0, 1.75, 0.75));
        assertThat(ProbabilityTable.SUPREME.gradeWeights(Tier.IRON)).isEmpty();
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
