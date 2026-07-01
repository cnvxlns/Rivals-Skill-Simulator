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

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(CardType.SIGNATURE_BLACK, TicketType.SUPREME_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            int blackCount = 0;
            for (int slotIndex = 0; slotIndex < response.getSlots().size(); slotIndex++) {
                Tier tier = response.getSlots().get(slotIndex).getSkill().getTier();
                if (tier == Tier.BLACK) {
                    blackCount++;
                    blackBySlot[slotIndex]++;
                } else {
                    assertThat(tier).isEqualTo(Tier.GOLD);
                }
            }
            assertThat(blackCount).isEqualTo(1);
        }

        for (int count : blackBySlot) {
            assertThat(rate(count, RATE_ROLLS)).isBetween(0.225, 0.275);
        }
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
    void wbcSignatureBlackPremiumAllowsAtMostOneBlackSlot() {
        SkillService service = serviceWith(
                normalPool(1L, 8),
                specialPool(100L, "WBC", "WBC_", 8),
                specialPool(200L, "BLACK", "BLACK_", 8),
                List.of(),
                List.of()
        );
        int blackPresentRuns = 0;

        for (int i = 0; i < RATE_ROLLS; i++) {
            RollResponse response = service.rollSkills(request(wbcSignatureBlackCardType(), TicketType.PREMIUM_SKILL_CHANGE));

            assertThat(response.getSlots()).hasSize(4);
            int blackCount = 0;
            for (var slot : response.getSlots()) {
                Tier tier = slot.getSkill().getTier();
                assertThat(tier).isIn(Tier.GOLD, Tier.WBC, Tier.BLACK);
                assertThat(slot.getLevel()).isEqualTo(Level.S);
                if (tier == Tier.BLACK) {
                    blackCount++;
                }
            }
            assertThat(blackCount).isLessThanOrEqualTo(1);
            if (blackCount == 1) {
                blackPresentRuns++;
            }
        }

        assertThat(rate(blackPresentRuns, RATE_ROLLS)).isBetween(0.13, 0.24);
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
            case "WBC", "BLACK" -> "1/1/1";
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
