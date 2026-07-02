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

        ScoreSkill standout = skills.stream()
                .filter(skill -> "M_029".equals(skill.getSkillKey()))
                .findFirst()
                .orElseThrow();

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

        for (String stat : List.of("파워", "정확", "선구")) {
            assertThat(valuesFor(skills, "M_032", stat, "ALWAYS")).containsExactly("7");
            assertThat(valuesFor(skills, "M_032", stat, "두번째타석까지")).containsExactly("5");
        }
    }

    @Test
    void powerPitcherAdditionalBatterDebuffsUseStatComparisonCondition() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

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

        for (String stat : List.of("파워", "정확", "선구", "인내", "주루", "수비")) {
            assertThat(valuesFor(skills, "M_042", stat, "ALWAYS")).containsExactly("11");
        }
        assertThat(valuesFor(skills, "M_042", "구위", "마에스트로누적")).containsExactly("12");
        assertThat(valuesFor(skills, "M_042", "변화", "마에스트로누적")).containsExactly("12");
        assertThat(alwaysValues(skills, "M_042", "구위")).isEmpty();
        assertThat(alwaysValues(skills, "M_042", "변화")).isEmpty();
    }

    @Test
    void playoffHeroOvrComparisonEffectsAreMarkedAsOvrUnderdogConditions() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);

        List<ScoreSkill> skills = readBundledScoreSkills(loader);

        assertThat(valuesFor(skills, "M_004", "구위", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_004", "변화", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_020", "파워", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_020", "정확", "OVR열세")).containsExactly("4");
        assertThat(valuesFor(skills, "M_004", "구위", "ALWAYS")).isEmpty();
        assertThat(valuesFor(skills, "M_020", "파워", "ALWAYS")).isEmpty();
    }

    @Test
    void durationCountsAreNotEncodedAsStandaloneStatIncreases() throws Exception {
        ScoreDataLoader loader = new ScoreDataLoader(null);
        List<ScoreSkill> skills = readBundledScoreSkills(loader);

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
