package com.example.skillsim.config;

import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDataLoaderTest {

    @Test
    void readScoreSkillsLinksEffectsBySkillKey() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        String skillsCsv = """
                skill_id,card_type,position,name,description
                S_001,NORMAL,BATTER,좌투선호,"좌투수 상대할 때 파워, 정확 능력치가 x 증가합니다"
                """;
        String effectsCsv = """
                skill_id,stat,condition,values
                S_001,파워,좌투상대,1/2/3/4/5/6/7/8/9
                S_001,정확,좌투상대,1/2/3/4/5/6/7/8/9
                """;

        List<ScoreSkill> skills = loader.readScoreSkills(new StringReader(skillsCsv), new StringReader(effectsCsv));

        assertThat(skills).hasSize(1);
        ScoreSkill skill = skills.get(0);
        assertThat(skill.getSkillKey()).isEqualTo("S_001");
        assertThat(skill.getCardType()).isEqualTo("NORMAL");
        assertThat(skill.getPosition()).isEqualTo("BATTER");
        assertThat(skill.getName()).isEqualTo("좌투선호");
        assertThat(skill.getEffects()).hasSize(2);
        assertThat(skill.getEffects())
                .allSatisfy(effect -> assertThat(effect.getSkill()).isSameAs(skill))
                .extracting("stat", "condition", "values")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("파워", "좌투상대", "1/2/3/4/5/6/7/8/9"),
                        org.assertj.core.groups.Tuple.tuple("정확", "좌투상대", "1/2/3/4/5/6/7/8/9")
                );
    }

    @Test
    void readStatWeightsReturnsStatToWeightMap() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        String weightsCsv = """
                stat,weight
                파워,1.10
                정확,0.90
                제구,0.00
                """;

        Map<String, Double> weights = loader.readStatWeights(new StringReader(weightsCsv));

        assertThat(weights).containsEntry("파워", 1.10);
        assertThat(weights).containsEntry("정확", 0.90);
        assertThat(weights).containsEntry("제구", 0.00);
    }

    @Test
    void standoutPitcherDataDoesNotEncodeBatterCountAsStatIncrease() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        java.util.Optional<ScoreSkill> standoutOpt = skills.stream()
                .filter(skill -> "M_029".equals(skill.getSkillKey()))
                .findFirst();
        if (standoutOpt.isEmpty()) {
            return;
        }
        ScoreSkill standout = standoutOpt.get();

        for (String stat : List.of("구위", "변화", "제구")) {
            assertThat(standout.getEffects().stream()
                    .filter(effect -> stat.equals(effect.getStat()))
                    .filter(effect -> "ALWAYS".equals(effect.getCondition()))
                    .map(ScoreEffect::getValues)
                    .toList()).containsExactly("7");
            assertThat(standout.getEffects().stream()
                    .filter(effect -> stat.equals(effect.getStat()))
                    .filter(effect -> "등판후9타자".equals(effect.getCondition()))
                    .map(ScoreEffect::getValues)
                    .toList()).containsExactly("5");
        }
    }

    @Test
    void standoutBatterDurationBonusUsesSecondPlateAppearanceCondition() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        if (skills.stream().noneMatch(skill -> "M_032".equals(skill.getSkillKey()))) {
            return;
        }

        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_032", stat, "ALWAYS")).containsExactly("7");
            assertThat(valuesFor(skills, "M_032", stat, "두번째타석까지")).containsExactly("5");
        }
    }

    @Test
    void powerPitcherAdditionalBatterDebuffsUseStatComparisonCondition() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        if (skills.stream().noneMatch(skill -> "M_041".equals(skill.getSkillKey()))) {
            return;
        }

        assertThat(valuesFor(skills, "M_041", "구위", "ALWAYS")).containsExactly("10");
        assertThat(valuesFor(skills, "M_041", "변화", "ALWAYS")).containsExactly("10");
        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_041", stat, "인내<구속")).containsExactly("10");
        }
    }

    @Test
    void maestroDataUsesCumulativeOutCountConditionForPitcherBuff() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        if (skills.stream().noneMatch(skill -> "M_042".equals(skill.getSkillKey()))) {
            return;
        }

        for (String stat : List.of("파워", "정확", "선구", "인내", "주루", "수비")) {
            assertThat(valuesFor(skills, "M_042", stat, "ALWAYS")).containsExactly("11");
        }
        assertThat(valuesFor(skills, "M_042", "구위", "마에스트로누적")).containsExactly("12");
        assertThat(valuesFor(skills, "M_042", "변화", "마에스트로누적")).containsExactly("12");
        assertThat(alwaysValues(skills, "M_042", "구위")).isEmpty();
        assertThat(alwaysValues(skills, "M_042", "변화")).isEmpty();
    }

    @Test
    void momentSkillsAndPositionExclusivesMatchUpdatedWorkbook() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        assertThat(positionFor(skills, "M_007")).isEqualTo("C, 2B, SS, CF");
        assertThat(positionFor(skills, "M_010")).isEqualTo("C, IF, OF");
        assertThat(positionFor(skills, "M_014")).isEqualTo("C");
        assertThat(positionFor(skills, "M_017")).isEqualTo("RP");
        assertThat(positionFor(skills, "M_019")).isEqualTo("SP");
        assertThat(positionFor(skills, "M_027")).isEqualTo("CP");
        assertThat(positionFor(skills, "M_043")).isEqualTo("CP");
        assertThat(positionFor(skills, "M_044")).isEqualTo("BATTER");

        for (String stat : List.of("구속", "변화", "구위", "제구")) {
            assertThat(valuesFor(skills, "M_043", stat, "ALWAYS")).containsExactly("9");
        }
        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_043", stat, "9회까지")).containsExactly("9");
            assertThat(valuesFor(skills, "M_044", stat, "파워정확합>주루수비합")).containsExactly("12");
            assertThat(valuesFor(skills, "M_044", stat, "포지션_DH")).containsExactly("6");
        }
    }

    @Test
    void playoffHeroOvrComparisonEffectsAreMarkedAsOvrUnderdogConditions() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        if (skills.stream().noneMatch(skill -> "M_004".equals(skill.getSkillKey()))) {
            return;
        }

        assertThat(valuesFor(skills, "M_004", "구위", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_004", "변화", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_020", "파워", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_020", "정확", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_004", "구위", "ALWAYS")).isEmpty();
        assertThat(valuesFor(skills, "M_020", "파워", "ALWAYS")).isEmpty();
    }

    @Test
    void conditionalNormalSkillBonusesAreNotMarkedAsAlwaysActive() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        assertThat(valuesFor(skills, "G_003", "파워", "높은공")).containsExactly("3/4/5/6/8/10/12/14/15");
        assertThat(valuesFor(skills, "G_003", "파워", "ALWAYS")).isEmpty();

        assertThat(valuesFor(skills, "G_011", "변화", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9");
        assertThat(valuesFor(skills, "G_011", "제구", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9");
        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "G_011", stat, "대타첫타석")).containsExactly("5/6/7/8/9/10/11/12/13");
            assertThat(valuesFor(skills, "G_011", stat, "ALWAYS")).isEmpty();
        }

        assertThat(valuesFor(skills, "G_049", "정확", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9");
        assertThat(valuesFor(skills, "G_049", "선구", "ALWAYS")).containsExactly("1/2/3/4/5/6/7/8/9");
        for (String stat : List.of("구위", "변화", "제구")) {
            assertThat(valuesFor(skills, "G_049", stat, "교체후첫타자")).containsExactly("5/6/7/8/9/10/11/12/13");
            assertThat(valuesFor(skills, "G_049", stat, "ALWAYS")).isEmpty();
        }

        for (String stat : List.of("구위", "변화", "제구", "구속")) {
            assertThat(valuesFor(skills, "G_043", stat, "등판후3타자")).containsExactly("2/3/4/5/6/7/8/9/10");
            assertThat(valuesFor(skills, "G_043", stat, "ALWAYS")).isEmpty();
        }

        for (String stat : List.of("정확", "선구", "인내")) {
            assertThat(valuesFor(skills, "G_048", stat, "포지션_SP+1_2회")).containsExactly("2/3/4/5/6/7/8/9/10");
            assertThat(valuesFor(skills, "G_048", stat, "포지션_SP")).isEmpty();
        }

        for (String stat : List.of("파워", "인내")) {
            assertThat(valuesFor(skills, "G_052", stat, "선발3_4_5+중계3_4_5+등판후4타자")).containsExactly("1/2/3/4/5/6/7/8/9");
            assertThat(valuesFor(skills, "G_052", stat, "선발3_4_5+중계3_4_5")).isEmpty();
        }

        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "G_056", stat, "비김또는리드+포지션_RP_CP+등판후3타자")).containsExactly("2/3/4/5/6/7/8/9/10");
            assertThat(valuesFor(skills, "G_056", stat, "비김또는리드+포지션_RP_CP")).isEmpty();
        }
    }

    @Test
    void durationCountsAreNotEncodedAsStandaloneStatIncreases() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        if (skills.stream().noneMatch(skill -> "M_029".equals(skill.getSkillKey()))) {
            return;
        }

        assertThat(alwaysValues(skills, "M_029", "구위")).doesNotContain("9");
        assertThat(alwaysValues(skills, "M_029", "변화")).doesNotContain("9");
        assertThat(alwaysValues(skills, "M_029", "제구")).doesNotContain("9");
        assertThat(alwaysValues(skills, "M_032", "파워")).doesNotContain("2");
        assertThat(alwaysValues(skills, "M_032", "정확")).doesNotContain("2");
        assertThat(alwaysValues(skills, "M_032", "선구")).doesNotContain("2");
        assertThat(alwaysValues(skills, "M_023", "구위")).doesNotContain("7");
        assertThat(alwaysValues(skills, "M_023", "변화")).doesNotContain("7");
        assertThat(alwaysValues(skills, "M_023", "구속")).doesNotContain("7");
        assertThat(alwaysValues(skills, "M_023", "제구")).doesNotContain("7");
        assertThat(alwaysValues(skills, "M_031", "구위")).doesNotContain("3");
        assertThat(alwaysValues(skills, "M_031", "변화")).doesNotContain("3");
    }

    private List<ScoreSkill> readBundledScoreSkills(ScoreDataLoader loader) throws Exception {
        try (Reader skillsReader = resourceReader("score_skills.csv");
             Reader effectsReader = resourceReader("score_effects.csv")) {
            return loader.readScoreSkills(skillsReader, effectsReader);
        }
    }

    private List<String> alwaysValues(List<ScoreSkill> skills, String skillKey, String stat) {
        return valuesFor(skills, skillKey, stat, "ALWAYS");
    }

    private String positionFor(List<ScoreSkill> skills, String skillKey) {
        return skills.stream()
                .filter(skill -> skillKey.equals(skill.getSkillKey()))
                .findFirst()
                .orElseThrow()
                .getPosition();
    }

    private List<String> valuesFor(List<ScoreSkill> skills, String skillKey, String stat, String condition) {
        return skills.stream()
                .filter(skill -> skillKey.equals(skill.getSkillKey()))
                .findFirst()
                .orElseThrow()
                .getEffects().stream()
                .filter(effect -> stat.equals(effect.getStat()))
                .filter(effect -> condition.equals(effect.getCondition()))
                .map(ScoreEffect::getValues)
                .toList();
    }

    private Reader resourceReader(String path) throws Exception {
        return new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
    }
}
