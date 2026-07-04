package com.example.skillsim.service;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.dto.SkillSlot;
import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import com.example.skillsim.repository.ScoreSkillRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillServiceTest {

    @Test
    void rollSkillsUsesScoreSkillDatasetAndReturnsSlotScores() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill(1L, "G_001", "NORMAL", "BATTER", "배팅머신",
                effect("파워", "ALWAYS", "2/3/4/5/6/7/8/9/10"));
        ScoreSkill silver = scoreSkill(2L, "S_001", "NORMAL", "BATTER", "좌투선호",
                effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"));
        ScoreSkill bronze = scoreSkill(3L, "B_001", "NORMAL", "BATTER", "초구공략",
                effect("파워", "ALWAYS", "1/2/3/4/5/6/7/8/9"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(gold, silver, bronze));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("파워", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE)
                .ticketType(TicketType.SUPREME_SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getTotalScore()).isGreaterThan(0.0);
        assertThat(response.getSlots())
                .allSatisfy(slot -> {
                    assertThat(slot.getSkill()).isNotNull();
                    assertThat(slot.getSkill().getName()).isNotBlank();
                    assertThat(slot.getSkill().getSkillId()).isNotBlank();
                    assertThat(slot.getScore()).isGreaterThan(0.0);
                });
    }

    @Test
    void rollSkillsSupportsWbcCardTypeWithThreeGoldScoredSlotsForNormalTicket() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill goldOne = scoreSkill(10L, "G_001", "NORMAL", "BATTER", "Gold Skill 1",
                effect("파워", "ALWAYS", "9/9/9/9/9/9/9/9/9"));
        ScoreSkill goldTwo = scoreSkill(11L, "G_002", "NORMAL", "BATTER", "Gold Skill 2",
                effect("파워", "ALWAYS", "9/9/9/9/9/9/9/9/9"));
        ScoreSkill goldThree = scoreSkill(12L, "G_003", "NORMAL", "BATTER", "Gold Skill 3",
                effect("파워", "ALWAYS", "9/9/9/9/9/9/9/9/9"));
        ScoreSkill first = scoreSkill(1L, "WBC_001", "WBC", "BATTER", "WORLD BASEBALL CLASSIC 플레이어",
                effect("파워", "ALWAYS", "9/11/13"));
        ScoreSkill second = scoreSkill(2L, "WBC_002", "WBC", "BATTER", "해결사",
                effect("파워", "ALWAYS", "7/9/11"));
        ScoreSkill third = scoreSkill(3L, "WBC_003", "WBC", "BATTER", "집중 상태",
                effect("파워", "ALWAYS", "8/9/10"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(goldOne, goldTwo, goldThree));
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(List.of(first, second, third));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("파워", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.WBC)
                .ticketType(TicketType.SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getTotalScore()).isGreaterThan(0.0);
        assertThat(response.getSlots())
                .allSatisfy(slot -> {
                    assertThat(slot.getSkill()).isNotNull();
                    assertThat(slot.getSkill().getTier().name()).isEqualTo("GOLD");
                    assertThat(slot.getLevel()).isEqualTo(Level.S);
                    assertThat(slot.getScore()).isGreaterThan(0.0);
                });
    }

    @Test
    void rollSkillsKeepsSupremeTicketFirstSlotGold() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"));
        ScoreSkill silver = scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5"));
        ScoreSkill bronze = scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(gold, silver, bronze));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE)
                .ticketType(TicketType.SUPREME_SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getSlots().get(0).getSkill().getSkillId()).isEqualTo("G_001");
        assertThat(response.getSlots().get(0).getSkill().getTier().name()).isEqualTo("GOLD");
    }

    @Test
    void rollSkillsKeepsSignatureBlackAtFourSlotsWithAtMostOneBlackSkill() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        ScoreSkill gold = scoreSkill(20L, "G_001", "NORMAL", "BATTER", "Gold Skill 1",
                effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"));
        ScoreSkill silver = scoreSkill(21L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                effect("POWER", "ALWAYS", "8/8/8/8/8/8/8/8/8"));
        ScoreSkill bronze = scoreSkill(22L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                effect("POWER", "ALWAYS", "6/6/6/6/6/6/6/6/6"));
        ScoreSkill iron = scoreSkill(23L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                effect("POWER", "ALWAYS", "4/4/4/4/4/4/4/4/4"));
        ScoreSkill first = scoreSkill(1L, "BLACK_001", "BLACK", "BATTER", "Black Skill 1",
                effect("POWER", "ALWAYS", "9/11/13"));
        ScoreSkill second = scoreSkill(2L, "BLACK_002", "BLACK", "BATTER", "Black Skill 2",
                effect("POWER", "ALWAYS", "8/10/12"));
        ScoreSkill third = scoreSkill(3L, "BLACK_003", "BLACK", "BATTER", "Black Skill 3",
                effect("POWER", "ALWAYS", "7/9/11"));
        ScoreSkill fourth = scoreSkill(4L, "BLACK_004", "BLACK", "BATTER", "Black Skill 4",
                effect("POWER", "ALWAYS", "6/8/10"));
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(gold, silver, bronze, iron));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(first, second, third, fourth));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE_BLACK)
                .ticketType(TicketType.PREMIUM_SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(4);
        assertThat(response.getSlots().stream()
                .filter(slot -> slot.getSkill().getTier() == com.example.skillsim.enums.Tier.BLACK)
                .count()).isLessThanOrEqualTo(1);
        assertThat(response.getSlots())
                .allSatisfy(slot -> {
                    assertThat(slot.getSkill()).isNotNull();
                    assertThat(slot.getScore()).isGreaterThan(0.0);
                });
    }

    @Test
    void signatureBlackNormalTicketNeverRollsBlackSkill() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(20L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(21L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "8/8/8/8/8/8/8/8/8")),
                scoreSkill(22L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "6/6/6/6/6/6/6/6/6")),
                scoreSkill(23L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                        effect("POWER", "ALWAYS", "4/4/4/4/4/4/4/4/4"))
        ));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(
                scoreSkill(1L, "BLACK_001", "BLACK", "BATTER", "Black Skill",
                        effect("POWER", "ALWAYS", "9/11/13"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        for (int i = 0; i < 1_000; i++) {
            RollResponse response = service.rollSkills(RollRequest.builder()
                    .cardType(CardType.SIGNATURE_BLACK)
                    .ticketType(TicketType.SKILL_CHANGE)
                    .position("BATTER")
                    .build());

            assertThat(response.getSlots()).hasSize(4);
            assertThat(response.getSlots())
                    .allSatisfy(slot -> assertThat(slot.getSkill().getTier())
                            .isNotEqualTo(com.example.skillsim.enums.Tier.BLACK));
        }
    }

    @Test
    void signatureBlackBlackSkillRollsBlackLevelDistribution() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill 1",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "8/8/8/8/8/8/8/8/8")),
                scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "6/6/6/6/6/6/6/6/6"))
        ));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(
                scoreSkill(10L, "BLACK_001", "BLACK", "BATTER", "Black Skill 1",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(11L, "BLACK_002", "BLACK", "BATTER", "Black Skill 2",
                        effect("POWER", "ALWAYS", "9/11/13"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        int nonSLevels = 0;
        for (int i = 0; i < 200; i++) {
            RollResponse response = service.rollSkills(RollRequest.builder()
                    .cardType(CardType.SIGNATURE_BLACK)
                    .ticketType(TicketType.SUPREME_SKILL_CHANGE)
                    .position("BATTER")
                    .build());

            assertThat(response.getSlots().stream()
                    .filter(slot -> slot.getSkill().getTier() == com.example.skillsim.enums.Tier.BLACK))
                    .singleElement()
                    .satisfies(slot -> assertThat(slot.getLevel()).isIn(Level.D, Level.C, Level.B, Level.A, Level.S));
            if (response.getSlots().stream()
                    .filter(slot -> slot.getSkill().getTier() == com.example.skillsim.enums.Tier.BLACK)
                    .findFirst()
                    .orElseThrow()
                    .getLevel() != Level.S) {
                nonSLevels++;
            }
        }
        assertThat(nonSLevels).isGreaterThan(0);
    }

    @Test
    void rollSkillsRejectsUnsupportedSlotLocks() {
        SkillService service = new SkillService(mock(ScoreSkillRepository.class), new ScoreCalculator(), () -> Map.of());

        assertThatThrownBy(() -> service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE)
                .ticketType(TicketType.SKILL_CHANGE)
                .position("BATTER")
                .lockedSlots(List.of(1))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only slot 1 lock is supported");

        assertThatThrownBy(() -> service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE_BLACK)
                .ticketType(TicketType.SKILL_CHANGE)
                .position("BATTER")
                .lockedSlots(List.of(0))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock not allowed");

        assertThatThrownBy(() -> service.rollSkills(RollRequest.builder()
                .cardType(CardType.WBC_SIGNATURE_BLACK)
                .ticketType(TicketType.SKILL_CHANGE)
                .position("BATTER")
                .lockedSlots(List.of(2))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock not allowed");
    }

    @Test
    void momentNonSupremeTicketsRollOnlyNormalTierSkills() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "7/7/7/7/7/7/7/7/7")),
                scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(4L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                        effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3"))
        ));
        when(repository.findByCardTypeIgnoreCase("MOMENT")).thenReturn(List.of(
                scoreSkill(10L, "M_001", "MOMENT", "BATTER", "Moment Skill 1",
                        effect("POWER", "ALWAYS", "20")),
                scoreSkill(11L, "M_002", "MOMENT", "BATTER", "Moment Skill 2",
                        effect("POWER", "ALWAYS", "20")),
                scoreSkill(12L, "M_003", "MOMENT", "BATTER", "Moment Skill 3",
                        effect("POWER", "ALWAYS", "20"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.MOMENT)
                .ticketType(TicketType.PREMIUM_SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getSlots())
                .allSatisfy(slot -> {
                    assertThat(slot.getSkill()).isNotNull();
                    assertThat(slot.getSkill().getTier()).isNotEqualTo(com.example.skillsim.enums.Tier.MOMENT);
                    assertThat(slot.getSkill().getSkillId()).doesNotStartWith("M_");
                });
    }

    @Test
    void momentCardAlwaysProtectsCurrentLevelsEvenWhenProtectionFlagsAreOff() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "7/7/7/7/7/7/7/7/7")),
                scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(4L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                        effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3"))
        ));
        when(repository.findByCardTypeIgnoreCase("MOMENT")).thenReturn(List.of(
                scoreSkill(10L, "M_001", "MOMENT", "BATTER", "Moment Skill",
                        effect("POWER", "ALWAYS", "20"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        for (TicketType ticketType : List.of(TicketType.PREMIUM_SKILL_CHANGE, TicketType.SUPREME_SKILL_CHANGE)) {
            for (int i = 0; i < 200; i++) {
                RollResponse response = service.rollSkills(RollRequest.builder()
                        .cardType(CardType.MOMENT)
                        .ticketType(ticketType)
                        .selectedTheme(ticketType == TicketType.SUPREME_SKILL_CHANGE ? "Moment Skill" : null)
                        .position("BATTER")
                        .currentLevels(List.of(Level.S, Level.S, Level.S))
                        .useLevelProtectionSlots(List.of(false, false, false))
                        .build());

                assertThat(response.getSlots()).hasSize(3);
                assertThat(response.getSlots())
                        .allSatisfy(slot -> assertThat(slot.getLevel()).isEqualTo(Level.S));
            }
        }
    }

    @Test
    void momentSkillChangeWithCurrentDLevelsUsesNormalGradeLadder() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "7/7/7/7/7/7/7/7/7")),
                scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(4L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                        effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));
        int[] levelCounts = new int[Level.values().length];

        for (int i = 0; i < 200; i++) {
            RollResponse response = service.rollSkills(RollRequest.builder()
                    .cardType(CardType.MOMENT)
                    .ticketType(TicketType.SKILL_CHANGE)
                    .position("BATTER")
                    .currentLevels(List.of(Level.D, Level.D, Level.D))
                    .useLevelProtectionSlots(List.of(false, false, false))
                    .build());

            assertThat(response.getSlots()).hasSize(3);
            response.getSlots().forEach(slot -> {
                assertThat(slot.getSkill().getTier()).isNotEqualTo(com.example.skillsim.enums.Tier.MOMENT);
                levelCounts[slot.getLevel().ordinal()]++;
            });
        }

        assertThat(levelCounts[Level.S.ordinal()]).isLessThan(200 * 3);
        assertThat(levelCounts[Level.D.ordinal()]).isGreaterThan(levelCounts[Level.C.ordinal()]);
        assertThat(levelCounts[Level.D.ordinal()]).isGreaterThan(levelCounts[Level.B.ordinal()]);
        assertThat(levelCounts[Level.D.ordinal()]).isGreaterThan(levelCounts[Level.A.ordinal()]);
        assertThat(levelCounts[Level.D.ordinal()]).isGreaterThan(levelCounts[Level.S.ordinal()]);
    }

    @Test
    void momentSkillChangeProtectionFloorsAtRealCurrentLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "S_001", "NORMAL", "BATTER", "Silver Skill",
                        effect("POWER", "ALWAYS", "7/7/7/7/7/7/7/7/7")),
                scoreSkill(3L, "B_001", "NORMAL", "BATTER", "Bronze Skill",
                        effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(4L, "I_001", "NORMAL", "BATTER", "Iron Skill",
                        effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));
        int bLevels = 0;

        for (int i = 0; i < 100; i++) {
            RollResponse response = service.rollSkills(RollRequest.builder()
                    .cardType(CardType.MOMENT)
                    .ticketType(TicketType.SKILL_CHANGE)
                    .position("BATTER")
                    .currentLevels(List.of(Level.B, Level.B, Level.B))
                    .useLevelProtectionSlots(List.of(false, false, false))
                    .build());

            assertThat(response.getSlots()).hasSize(3);
            for (SkillSlot slot : response.getSlots()) {
                assertThat(slot.getLevel()).isNotIn(Level.D, Level.C);
                if (slot.getLevel() == Level.B) {
                    bLevels++;
                }
            }
        }

        assertThat(bLevels).isGreaterThan(0);
    }

    @Test
    void wbcNormalTicketRollsGoldSkillAtSLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill 1",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "G_002", "NORMAL", "BATTER", "Gold Skill 2",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(3L, "G_003", "NORMAL", "BATTER", "Gold Skill 3",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"))
        ));
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(List.of(
                scoreSkill(10L, "WBC_001", "WBC", "BATTER", "WBC Skill 1",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(11L, "WBC_002", "WBC", "BATTER", "WBC Skill 2",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(12L, "WBC_003", "WBC", "BATTER", "WBC Skill 3",
                        effect("POWER", "ALWAYS", "9/11/13"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.WBC)
                .ticketType(TicketType.SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getSlots())
                .allSatisfy(slot -> {
                    assertThat(slot.getSkill()).isNotNull();
                    assertThat(slot.getSkill().getTier()).isEqualTo(com.example.skillsim.enums.Tier.GOLD);
                    assertThat(slot.getLevel()).isEqualTo(Level.S);
                });
    }

    @Test
    void signatureBlackSupremeTicketRollsExactlyOneBlackSlotWithSlotOneGoldWhenNonBlack() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold Skill 1",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "G_002", "NORMAL", "BATTER", "Gold Skill 2",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(3L, "G_003", "NORMAL", "BATTER", "Gold Skill 3",
                        effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"))
        ));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(
                scoreSkill(10L, "BLACK_001", "BLACK", "BATTER", "Black Skill 1",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(11L, "BLACK_002", "BLACK", "BATTER", "Black Skill 2",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(12L, "BLACK_003", "BLACK", "BATTER", "Black Skill 3",
                        effect("POWER", "ALWAYS", "9/11/13")),
                scoreSkill(13L, "BLACK_004", "BLACK", "BATTER", "Black Skill 4",
                        effect("POWER", "ALWAYS", "9/11/13"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.rollSkills(RollRequest.builder()
                .cardType(CardType.SIGNATURE_BLACK)
                .ticketType(TicketType.SUPREME_SKILL_CHANGE)
                .position("BATTER")
                .build());

        assertThat(response.getSlots()).hasSize(4);
        assertThat(response.getSlots().stream()
                .filter(slot -> slot.getSkill().getTier() == com.example.skillsim.enums.Tier.BLACK))
                .hasSize(1);
        for (int slotIndex = 0; slotIndex < response.getSlots().size(); slotIndex++) {
            com.example.skillsim.enums.Tier tier = response.getSlots().get(slotIndex).getSkill().getTier();
            if (tier == com.example.skillsim.enums.Tier.BLACK) {
                continue;
            }
            if (slotIndex == 0) {
                assertThat(tier).isEqualTo(com.example.skillsim.enums.Tier.GOLD);
            } else {
                assertThat(tier).isIn(
                        com.example.skillsim.enums.Tier.BRONZE,
                        com.example.skillsim.enums.Tier.SILVER,
                        com.example.skillsim.enums.Tier.GOLD
                );
            }
        }
    }

    @Test
    void initialSlotsSignatureUseDLevelIronOrBronze() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "I_001", "NORMAL", "BATTER", "Iron 1", effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3")),
                scoreSkill(2L, "I_002", "NORMAL", "BATTER", "Iron 2", effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3")),
                scoreSkill(3L, "I_003", "NORMAL", "BATTER", "Iron 3", effect("POWER", "ALWAYS", "3/3/3/3/3/3/3/3/3")),
                scoreSkill(4L, "B_001", "NORMAL", "BATTER", "Bronze 1", effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(5L, "B_002", "NORMAL", "BATTER", "Bronze 2", effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5")),
                scoreSkill(6L, "B_003", "NORMAL", "BATTER", "Bronze 3", effect("POWER", "ALWAYS", "5/5/5/5/5/5/5/5/5"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        for (int i = 0; i < 50; i++) {
            RollResponse response = service.initialSlots("SIGNATURE", "BATTER", null);
            assertThat(response.getSlots()).hasSize(3);
            assertThat(response.getSlots()).allSatisfy(slot -> {
                assertThat(slot.getLevel()).isEqualTo(Level.D);
                assertThat(slot.getSkill().getTier())
                        .isIn(com.example.skillsim.enums.Tier.IRON, com.example.skillsim.enums.Tier.BRONZE);
            });
        }
    }

    @Test
    void initialSlotsWbcUseThreeGoldSkillsAtSLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold 1", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "G_002", "NORMAL", "BATTER", "Gold 2", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(3L, "G_003", "NORMAL", "BATTER", "Gold 3", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.initialSlots("WBC", "BATTER", null);

        assertThat(response.getSlots()).hasSize(3);
        assertThat(response.getSlots()).allSatisfy(slot -> {
            assertThat(slot.getLevel()).isEqualTo(Level.S);
            assertThat(slot.getSkill().getTier()).isEqualTo(com.example.skillsim.enums.Tier.GOLD);
        });
    }

    @Test
    void initialSlotsSignatureBlackHasGoldSlotsAndFinalBlackSlotAllSLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold 1", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "G_002", "NORMAL", "BATTER", "Gold 2", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(3L, "G_003", "NORMAL", "BATTER", "Gold 3", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"))
        ));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(
                scoreSkill(9L, "BL_001", "BLACK", "BATTER", "Black 1", effect("POWER", "ALWAYS", "20/20/20"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        RollResponse response = service.initialSlots("SIGNATURE_BLACK", "BATTER", null);

        assertThat(response.getSlots()).hasSize(4);
        assertThat(response.getSlots()).allSatisfy(slot -> assertThat(slot.getLevel()).isEqualTo(Level.S));
        assertThat(response.getSlots().subList(0, 3)).allSatisfy(slot ->
                assertThat(slot.getSkill().getTier()).isEqualTo(com.example.skillsim.enums.Tier.GOLD));
        assertThat(response.getSlots().get(3).getSkill().getTier()).isEqualTo(com.example.skillsim.enums.Tier.BLACK);
    }

    @Test
    void initialSlotsWbcSignatureBlackMixesGoldWbcAndFinalBlackAllSLevel() {
        ScoreSkillRepository repository = mock(ScoreSkillRepository.class);
        when(repository.findByCardTypeIgnoreCase("NORMAL")).thenReturn(List.of(
                scoreSkill(1L, "G_001", "NORMAL", "BATTER", "Gold 1", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(2L, "G_002", "NORMAL", "BATTER", "Gold 2", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10")),
                scoreSkill(3L, "G_003", "NORMAL", "BATTER", "Gold 3", effect("POWER", "ALWAYS", "10/10/10/10/10/10/10/10/10"))
        ));
        when(repository.findByCardTypeIgnoreCase("WBC")).thenReturn(List.of(
                scoreSkill(7L, "WBC_001", "WBC", "BATTER", "Wbc 1", effect("POWER", "ALWAYS", "12/12/12")),
                scoreSkill(8L, "WBC_002", "WBC", "BATTER", "Wbc 2", effect("POWER", "ALWAYS", "12/12/12")),
                scoreSkill(10L, "WBC_003", "WBC", "BATTER", "Wbc 3", effect("POWER", "ALWAYS", "12/12/12"))
        ));
        when(repository.findByCardTypeIgnoreCase("BLACK")).thenReturn(List.of(
                scoreSkill(9L, "BL_001", "BLACK", "BATTER", "Black 1", effect("POWER", "ALWAYS", "20/20/20"))
        ));
        SkillService service = new SkillService(repository, new ScoreCalculator(), () -> Map.of("POWER", 1.0));

        for (int i = 0; i < 50; i++) {
            RollResponse response = service.initialSlots("WBC_SIGNATURE_BLACK", "BATTER", null);
            assertThat(response.getSlots()).hasSize(4);
            assertThat(response.getSlots()).allSatisfy(slot -> assertThat(slot.getLevel()).isEqualTo(Level.S));
            assertThat(response.getSlots().subList(0, 3)).allSatisfy(slot ->
                    assertThat(slot.getSkill().getTier())
                            .isIn(com.example.skillsim.enums.Tier.GOLD, com.example.skillsim.enums.Tier.WBC));
            assertThat(response.getSlots().get(3).getSkill().getTier()).isEqualTo(com.example.skillsim.enums.Tier.BLACK);
        }
    }

    private ScoreSkill scoreSkill(Long id, String skillKey, String cardType, String position, String name, ScoreEffect... effects) {
        ScoreSkill skill = ScoreSkill.builder()
                .id(id)
                .skillKey(skillKey)
                .cardType(cardType)
                .position(position)
                .name(name)
                .description(name)
                .build();
        for (ScoreEffect effect : effects) {
            effect.setSkill(skill);
            skill.getEffects().add(effect);
        }
        return skill;
    }

    private ScoreEffect effect(String stat, String condition, String values) {
        return ScoreEffect.builder()
                .stat(stat)
                .condition(condition)
                .values(values)
                .build();
    }
}
