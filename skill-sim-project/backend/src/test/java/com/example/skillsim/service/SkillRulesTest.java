package com.example.skillsim.service;

import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.Tier;
import com.example.skillsim.model.ScoreSkill;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillRulesTest {

    @Test
    void slotCountsMatchCardTypeRules() {
        assertThat(SkillRules.slotCount(CardType.SIGNATURE)).isEqualTo(3);
        assertThat(SkillRules.slotCount(CardType.HOF)).isEqualTo(3);
        assertThat(SkillRules.slotCount(CardType.WBC)).isEqualTo(3);
        assertThat(SkillRules.slotCount(CardType.MOMENT)).isEqualTo(3);
        assertThat(SkillRules.slotCount(CardType.SUPREME_MOMENT)).isEqualTo(3);
        assertThat(SkillRules.slotCount(CardType.SIGNATURE_BLACK)).isEqualTo(4);
        assertThat(SkillRules.slotCount("WBC_SIGNATURE_BLACK")).isEqualTo(4);
        assertThat(SkillRules.slotCount("BLACK")).isEqualTo(4);
    }

    @Test
    void gradeLaddersMatchCardTypeRules() {
        assertThat(SkillRules.gradeLadder("NORMAL"))
                .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4);
        assertThat(SkillRules.gradeLadder("SIGNATURE"))
                .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2, Level.S3, Level.S4);
        assertThat(SkillRules.gradeLadder("HOF"))
                .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1);
        assertThat(SkillRules.gradeLadder("WBC"))
                .containsExactly(Level.S, Level.S1, Level.S2);
        assertThat(SkillRules.gradeLadder("SIGNATURE_BLACK"))
                .containsExactly(Level.D, Level.C, Level.B, Level.A, Level.S, Level.S1, Level.S2);
        assertThat(SkillRules.gradeLadder("WBC_SIGNATURE_BLACK"))
                .containsExactly(Level.S, Level.S1, Level.S2);
        assertThat(SkillRules.gradeLadder("MOMENT"))
                .containsExactly(Level.S);
        assertThat(SkillRules.gradeLadder("SUPREME_MOMENT"))
                .containsExactly(Level.S);
    }

    @Test
    void blackSkillMaxLevelClampsToValueColumnsWithinExpandedLadder() {
        ScoreSkill skill = skill("BLACK_001", "BLACK");
        skill.getEffects().add(effect("5/8/11"));

        assertThat(SkillRules.maxLevel(skill)).isEqualTo(3);
        assertThat(SkillRules.gradeLabels("BLACK", SkillRules.maxLevel(skill)))
                .containsExactly("D", "C", "B");
    }

    @Test
    void normalizeCardTypeRejectsPrimeAndKeepsWbcSignatureBlackDistinct() {
        assertThatThrownBy(() -> SkillRules.normalizeCardType("PRIME"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SkillRules.normalizeCardType("WBC_SIGNATURE_BLACK")).isEqualTo("WBC_BLACK");
    }

    @Test
    void rollTierUsesCardTypeAndSkillKeyRules() {
        assertThat(SkillRules.rollTier(skill("I_001", "NORMAL"))).isEqualTo(Tier.IRON);
        assertThat(SkillRules.rollTier(skill("B_001", "NORMAL"))).isEqualTo(Tier.BRONZE);
        assertThat(SkillRules.rollTier(skill("S_001", "NORMAL"))).isEqualTo(Tier.SILVER);
        assertThat(SkillRules.rollTier(skill("G_001", "NORMAL"))).isEqualTo(Tier.GOLD);
        assertThat(SkillRules.rollTier(skill("BLACK_001", "BLACK"))).isEqualTo(Tier.BLACK);
        assertThat(SkillRules.rollTier(skill("MOMENT_001", "MOMENT"))).isEqualTo(Tier.MOMENT);
        assertThat(SkillRules.rollTier(skill("WBC_001", "WBC"))).isEqualTo(Tier.WBC);
        assertThat(SkillRules.rollTier(skill("HOF_001", "HOF"))).isEqualTo(Tier.HOF);
        assertThat(SkillRules.rollTier(skill("G_001", "HOF"))).isEqualTo(Tier.GOLD);
    }

    @Test
    void matchesDetailedMomentPositionExclusivesIncludingInfieldAlias() {
        String upTheMiddle = "C, 2B, SS, CF";
        assertThat(SkillRules.matchesPosition(upTheMiddle, "BATTER")).isTrue();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "C")).isTrue();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "2B")).isTrue();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "SS")).isTrue();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "CF")).isTrue();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "1B")).isFalse();
        assertThat(SkillRules.matchesPosition(upTheMiddle, "LF")).isFalse();

        String allAround = "C, IF, OF";
        assertThat(SkillRules.matchesPosition(allAround, "BATTER")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "IF")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "1B")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "2B")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "3B")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "SS")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "LF")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "CF")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "RF")).isTrue();
        assertThat(SkillRules.matchesPosition(allAround, "DH")).isFalse();
    }

    @Test
    void detailedPitcherExclusivesStillMatchTheirBroadPosition() {
        assertThat(SkillRules.matchesPosition("SP", "PITCHER")).isTrue();
        assertThat(SkillRules.matchesPosition("RP", "PITCHER")).isTrue();
        assertThat(SkillRules.matchesPosition("CP", "PITCHER")).isTrue();
        assertThat(SkillRules.matchesPosition("CP", "RP")).isFalse();
    }

    private static ScoreSkill skill(String skillKey, String cardType) {
        return ScoreSkill.builder()
                .skillKey(skillKey)
                .cardType(cardType)
                .position("BATTER")
                .name(skillKey)
                .description(skillKey)
                .build();
    }

    private static com.example.skillsim.model.ScoreEffect effect(String values) {
        return com.example.skillsim.model.ScoreEffect.builder()
                .stat("POWER")
                .condition("ALWAYS")
                .values(values)
                .build();
    }
}
