package com.example.skillsim.service;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceDistributionTest {

    private static final int RATE_ROLLS = 6_000;
    private static final int SIGNATURE_BLACK_ROLLS = 20_000;
    private static final double POWER_WEIGHT = 1.0;

    @Test
    void wbcTicketRatesMatchDocumentedRates() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                specialPool(100L, "WBC", "WBC_", 8),
                List.of(),
                List.of(),
                List.of()
        );

        WbcStats skillChange = rollWbc(service, TicketType.SKILL_CHANGE);
        WbcStats premium = rollWbc(service, TicketType.PREMIUM_SKILL_CHANGE);
        WbcStats supreme = rollWbc(service, TicketType.SUPREME_SKILL_CHANGE);

        assertThat(skillChange.wbcSlots).isZero();
        assertThat(skillChange.nonWbcSlots()).isEqualTo(skillChange.totalSlots());
        assertThat(skillChange.nonSLevelSlots).isZero();
        assertThat(premium.rate()).isBetween(0.0025, 0.0075);
        assertThat(premium.nonSLevelSlots).isZero();
        assertThat(supreme.rate()).isBetween(0.085, 0.115);
        assertThat(supreme.nonSLevelSlots).isZero();
    }

    @Test
    void signatureBlackSupremePlacesExactlyOneBlackSlotUniformly() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                List.of(),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );
        int[] blackBySlot = new int[4];
        int[] blackLevelCounts = new int[Level.values().length];
        int[] nonBlackTierCounts = new int[Tier.values().length];
        int nonBlackSlotsAfterSlotOne = 0;
        int slotOneNonBlackCount = 0;

        for (int i = 0; i < SIGNATURE_BLACK_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(CardType.SIGNATURE_BLACK, TicketType.SUPREME_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            int blackCount = 0;
            for (int slotIndex = 0; slotIndex < response.getSlots().size(); slotIndex++) {
                Tier tier = response.getSlots().get(slotIndex).getSkill().getTier();
                if (tier == Tier.BLACK) {
                    blackCount++;
                    blackBySlot[slotIndex]++;
                    Level level = response.getSlots().get(slotIndex).getLevel();
                    assertThat(level).isIn(Level.D, Level.C, Level.B, Level.A, Level.S);
                    blackLevelCounts[level.ordinal()]++;
                } else {
                    if (slotIndex == 0) {
                        slotOneNonBlackCount++;
                        assertThat(tier).isEqualTo(Tier.GOLD);
                    } else {
                        assertThat(tier).isIn(Tier.BRONZE, Tier.SILVER, Tier.GOLD);
                        nonBlackTierCounts[tier.ordinal()]++;
                        nonBlackSlotsAfterSlotOne++;
                    }
                }
            }
            assertThat(blackCount).isEqualTo(1);
        }

        for (int count : blackBySlot) {
            assertThat(rate(count, SIGNATURE_BLACK_ROLLS)).isBetween(0.225, 0.275);
        }
        assertThat(slotOneNonBlackCount).isGreaterThan(0);
        assertSignatureBlackLevelDistribution(blackLevelCounts, SIGNATURE_BLACK_ROLLS);
        assertThat(rate(nonBlackTierCounts[Tier.BRONZE.ordinal()], nonBlackSlotsAfterSlotOne)).isBetween(0.17, 0.23);
        assertThat(rate(nonBlackTierCounts[Tier.SILVER.ordinal()], nonBlackSlotsAfterSlotOne)).isBetween(0.27, 0.33);
        assertThat(rate(nonBlackTierCounts[Tier.GOLD.ordinal()], nonBlackSlotsAfterSlotOne)).isBetween(0.47, 0.53);
    }

    @Test
    void signatureBlackPremiumPlacesBlackAtTwentyPercentOverallAndFivePercentPerSlot() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                List.of(),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );

        assertPremiumBlackDistribution(service, CardType.SIGNATURE_BLACK);
    }

    @Test
    void wbcSignatureBlackSkillChangeRollsFourGoldSLevelSlots() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                specialPool(100L, "WBC", "WBC_", 8),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );

        for (int i = 0; i < 200; i++) {
            RollResponse response = service.rollSkills(request(wbcSignatureBlackCardType(), TicketType.SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            assertThat(response.getSlots())
                    .allSatisfy(slot -> {
                        assertThat(slot.getSkill().getTier()).isEqualTo(Tier.GOLD);
                        assertThat(slot.getLevel()).isEqualTo(Level.S);
                    });
        }
    }

    @Test
    void wbcSignatureBlackPremiumPlacesBlackAtTwentyPercentOverallAndFivePercentPerSlot() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                specialPool(100L, "WBC", "WBC_", 8),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );

        assertPremiumBlackDistribution(service, wbcSignatureBlackCardType());
    }

    private void assertPremiumBlackDistribution(SkillService service, CardType cardType) {
        int blackPresentRuns = 0;
        int[] blackBySlot = new int[4];
        int[] blackLevelCounts = new int[Level.values().length];
        int wbcSlots = 0;
        int totalSlots = 0;

        for (int i = 0; i < SIGNATURE_BLACK_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(cardType, TicketType.PREMIUM_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            int blackCount = 0;
            for (int slotIndex = 0; slotIndex < response.getSlots().size(); slotIndex++) {
                var slot = response.getSlots().get(slotIndex);
                Tier tier = slot.getSkill().getTier();
                totalSlots++;
                if (cardType == CardType.WBC_SIGNATURE_BLACK) {
                    assertThat(tier).isIn(Tier.GOLD, Tier.WBC, Tier.BLACK);
                    assertThat(slot.getLevel()).isEqualTo(Level.S);
                }
                if (tier == Tier.BLACK) {
                    blackCount++;
                    blackBySlot[slotIndex]++;
                    if (cardType == CardType.WBC_SIGNATURE_BLACK) {
                        assertThat(slot.getLevel()).isEqualTo(Level.S);
                    } else {
                        Level level = slot.getLevel();
                        assertThat(level).isIn(Level.D, Level.C, Level.B, Level.A, Level.S);
                        blackLevelCounts[level.ordinal()]++;
                    }
                } else if (tier == Tier.WBC) {
                    wbcSlots++;
                }
            }
            assertThat(blackCount).isLessThanOrEqualTo(1);
            if (blackCount == 1) {
                blackPresentRuns++;
            }
        }

        assertThat(rate(blackPresentRuns, SIGNATURE_BLACK_ROLLS)).isBetween(0.185, 0.215);
        for (int count : blackBySlot) {
            assertThat(rate(count, SIGNATURE_BLACK_ROLLS)).isBetween(0.043, 0.057);
        }
        if (cardType == CardType.WBC_SIGNATURE_BLACK) {
            // Black is pre-drawn, so the conditional WBC roll must still produce a 0.005 marginal per-slot rate.
            assertThat(rate(wbcSlots, totalSlots)).isBetween(0.004, 0.006);
        } else {
            assertSignatureBlackLevelDistribution(blackLevelCounts, blackPresentRuns);
        }
    }

    private void assertSignatureBlackLevelDistribution(int[] levelCounts, int totalBlackSlots) {
        assertThat(rate(levelCounts[Level.D.ordinal()], totalBlackSlots)).isBetween(0.37, 0.43);
        assertThat(rate(levelCounts[Level.C.ordinal()], totalBlackSlots)).isBetween(0.27, 0.33);
        assertThat(rate(levelCounts[Level.B.ordinal()], totalBlackSlots)).isBetween(0.17, 0.23);
        assertThat(rate(levelCounts[Level.A.ordinal()], totalBlackSlots)).isBetween(0.04, 0.10);
        assertThat(rate(levelCounts[Level.S.ordinal()], totalBlackSlots)).isBetween(0.00, 0.06);
    }

    @Test
    void wbcSignatureBlackSupremeGuaranteesOneUniformBlackSlotAndGoldOrWbcRest() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                specialPool(100L, "WBC", "WBC_", 8),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );
        int[] blackBySlot = new int[4];

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(wbcSignatureBlackCardType(), TicketType.SUPREME_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            int blackCount = 0;
            for (int slotIndex = 0; slotIndex < response.getSlots().size(); slotIndex++) {
                Tier tier = response.getSlots().get(slotIndex).getSkill().getTier();
                assertThat(response.getSlots().get(slotIndex).getLevel()).isEqualTo(Level.S);
                if (tier == Tier.BLACK) {
                    blackCount++;
                    blackBySlot[slotIndex]++;
                } else {
                    assertThat(tier).isIn(Tier.GOLD, Tier.WBC);
                }
            }
            assertThat(blackCount).isEqualTo(1);
        }

        for (int count : blackBySlot) {
            assertThat(rate(count, RATE_ROLLS)).isBetween(0.225, 0.275);
        }
    }

    @Test
    void momentSupremeOnlySlotOneCanRollSelectedMomentTheme() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                List.of(),
                List.of(),
                specialPool(300L, "MOMENT", "M_", 4),
                List.of()
        );
        int momentSlotOne = 0;

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(RollRequest.builder()
                    .cardType(CardType.MOMENT)
                    .ticketType(TicketType.SUPREME_SKILL_CHANGE)
                    .selectedTheme("MOMENT Skill 1")
                    .position("BATTER")
                    .build());

            assertThat(response.getSlots()).hasSize(3);
            if (response.getSlots().get(0).getSkill().getTier() == Tier.MOMENT) {
                momentSlotOne++;
            }
            assertThat(response.getSlots().get(1).getSkill().getTier()).isNotEqualTo(Tier.MOMENT);
            assertThat(response.getSlots().get(2).getSkill().getTier()).isNotEqualTo(Tier.MOMENT);
        }

        assertThat(rate(momentSlotOne, RATE_ROLLS)).isBetween(0.048, 0.073);
    }

    @Test
    void hofSupremeUsesDocumentedSlotTierPools() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                List.of(),
                List.of(),
                List.of(),
                specialPool(400L, "HOF", "HOF_", 8)
        );
        int slotOneHof = 0;
        int otherSlotHof = 0;

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(CardType.HOF, TicketType.SUPREME_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(3);
            Tier slotOneTier = response.getSlots().get(0).getSkill().getTier();
            assertThat(slotOneTier).isIn(Tier.GOLD, Tier.HOF);
            if (slotOneTier == Tier.HOF) {
                slotOneHof++;
            }

            for (int slotIndex = 1; slotIndex < response.getSlots().size(); slotIndex++) {
                Tier tier = response.getSlots().get(slotIndex).getSkill().getTier();
                assertThat(tier).isNotEqualTo(Tier.IRON);
                if (tier == Tier.HOF) {
                    otherSlotHof++;
                }
            }
        }

        assertThat(rate(slotOneHof, RATE_ROLLS)).isBetween(0.085, 0.115);
        assertThat(rate(otherSlotHof, RATE_ROLLS * 2)).isBetween(0.085, 0.115);
    }

    private WbcStats rollWbc(SkillService service, TicketType ticketType) {
        int wbcSlots = 0;
        int totalSlots = 0;
        int nonSLevelSlots = 0;

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(CardType.WBC, ticketType));
            for (var slot : response.getSlots()) {
                totalSlots++;
                if (slot.getSkill().getTier() == Tier.WBC) {
                    wbcSlots++;
                }
                if (slot.getLevel() != Level.S) {
                    nonSLevelSlots++;
                }
            }
        }

        return new WbcStats(wbcSlots, totalSlots, nonSLevelSlots);
    }

    private SkillService serviceWith(
            List<ScoreSkill> normalSkills,
            List<ScoreSkill> wbcSkills,
            List<ScoreSkill> blackSkills,
            List<ScoreSkill> momentSkills,
            List<ScoreSkill> hofSkills
    ) {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(normalSkills);
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(wbcSkills);
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(blackSkills);
        when(repository.findByCardTypeIgnoreCase("MOMENT")).thenReturn(momentSkills);
        when(repository.findByCardTypeIgnoreCase("HOF")).thenReturn(hofSkills);
        return new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", POWER_WEIGHT));
    }

    private RollRequest request(CardType cardType, TicketType ticketType) {
        return RollRequest.builder()
                .cardType(cardType)
                .ticketType(ticketType)
                .position("BATTER")
                .build();
    }

    private CardType wbcSignatureBlackCardType() {
        return CardType.valueOf("WBC_SIGNATURE_BLACK");
    }

    private List<ScoreSkill> normalPool(long firstId, int countPerTier) {
        List<ScoreSkill> skills = new ArrayList<>();
        long id = firstId;
        skills.addAll(skills(id, "NORMAL", "G_", "Gold", countPerTier, normalValues()));
        id += countPerTier;
        skills.addAll(skills(id, "NORMAL", "S_", "Silver", countPerTier, normalValues()));
        id += countPerTier;
        skills.addAll(skills(id, "NORMAL", "B_", "Bronze", countPerTier, normalValues()));
        id += countPerTier;
        skills.addAll(skills(id, "NORMAL", "I_", "Iron", countPerTier, normalValues()));
        return skills;
    }

    private List<ScoreSkill> specialPool(long firstId, String cardType, String prefix, int count) {
        String values = switch (cardType) {
            case "MOMENT" -> "1";
            case "WBC" -> "1/1/1";
            case "BLACK" -> "1/1/1/1/1";
            case "HOF" -> "1/1/1/1/1/1";
            default -> normalValues();
        };
        return skills(firstId, cardType, prefix, cardType, count, values);
    }

    private List<ScoreSkill> skills(long firstId, String cardType, String prefix, String namePrefix, int count, String values) {
        List<ScoreSkill> skills = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = firstId + i;
            skills.add(scoreSkill(id, prefix + String.format("%03d", i + 1), cardType, namePrefix + " Skill " + (i + 1), values));
        }
        return skills;
    }

    private ScoreSkill scoreSkill(long id, String skillKey, String cardType, String name, String values) {
        ScoreSkill skill = ScoreSkill.builder()
                .id(id)
                .skillKey(skillKey)
                .cardType(cardType)
                .position("BATTER")
                .name(name)
                .description(name)
                .build();
        ScoreEffect effect = ScoreEffect.builder()
                .stat("POWER")
                .condition("ALWAYS")
                .values(values)
                .build();
        effect.setSkill(skill);
        skill.getEffects().add(effect);
        return skill;
    }

    private String normalValues() {
        return "1/1/1/1/1/1/1/1/1";
    }

    private double rate(int count, int total) {
        return (double) count / total;
    }

    private record WbcStats(int wbcSlots, int totalSlots, int nonSLevelSlots) {
        int nonWbcSlots() {
            return totalSlots - wbcSlots;
        }

        double rate() {
            return (double) wbcSlots / totalSlots;
        }
    }
}
