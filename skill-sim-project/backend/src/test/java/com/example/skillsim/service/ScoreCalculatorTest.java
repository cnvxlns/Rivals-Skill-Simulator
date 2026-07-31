package com.example.skillsim.service;

import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ScoreCalculatorTest {

    @Test
    void calculatesTotalPerSkillAndPerStatWithWeightsAndConditions() {
        ScoreSkill skill = scoreSkill("S_001", "좌투선호",
                effect("파워", "ALWAYS", "1/2/3"),
                effect("정확", "주자있음", "2/4/6"),
                effect("제구", "ALWAYS", "10/10/10"),
                effect("구위", "모드_리그", "100/100/100")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 2)),
                Map.of("파워", 1.10, "정확", 0.90, "제구", 0.00, "구위", 1.20),
                Map.of("주자있음", 0.40, "모드_리그", 0.00)
        );

        assertThat(result.total()).isEqualTo(3.64);
        // perStat 은 스킬이 올려주는 스탯 절대치(순수 증가량)이므로 weight·조건확률을 제외한 value 만 표시한다.
        assertThat(result.perStat()).containsEntry("파워", 2.00);
        assertThat(result.perStat()).containsEntry("정확", 4.00);
        assertThat(result.perStat()).containsEntry("제구", 10.00);
        assertThat(result.perStat()).containsEntry("구위", 100.00);
        assertThat(result.perSkill()).hasSize(1);
        assertThat(result.perSkill().get(0).skillKey()).isEqualTo("S_001");
        assertThat(result.perSkill().get(0).score()).isEqualTo(3.64);
        assertThat(result.perSkill().get(0).perStat()).containsEntry("정확", 4.00);
    }

    @Test
    void clampsLevelToAvailableValueRange() {
        ScoreSkill skill = scoreSkill("S_002", "우투선호", effect("파워", "ALWAYS", "5/7"));
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result belowMin = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 0)),
                Map.of("파워", 1.0),
                Map.of()
        );
        ScoreCalculator.Result aboveMax = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 9)),
                Map.of("파워", 1.0),
                Map.of()
        );

        assertThat(belowMin.total()).isEqualTo(5.00);
        assertThat(aboveMax.total()).isEqualTo(7.00);
    }

    @Test
    void treatsMultipleModeConditionsAsAlternatives() {
        ScoreSkill skill = scoreSkill("G_016", "순위경쟁",
                effect("파워", "모드_랭킹대전+모드_라이브매치", "3")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                Map.of("모드_랭킹대전", 1.0, "모드_라이브매치", 0.0)
        );

        assertThat(result.total()).isEqualTo(3.00);
    }

    @Test
    void appliesRoleBasedInningConditionProbabilities() {
        ScoreSkill skill = scoreSkill("G_018", "후반 집중력",
                effect("파워", "7회이후", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result batter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );
        ScoreCalculator.Result starter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );
        ScoreCalculator.Result reliever = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
                Map.of()
        );
        ScoreCalculator.Result closer = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("CP"),
                Map.of()
        );

        assertThat(batter.total()).isEqualTo(2.50);
        assertThat(starter.total()).isEqualTo(1.20);
        assertThat(reliever.total()).isEqualTo(7.90);
        assertThat(closer.total()).isEqualTo(10.00);
    }

    @Test
    void appliesRoleBasedInningRangeAndUntilProbabilities() {
        Map<String, Double> starter = ScoreCalculator.conditionProbabilitiesForPosition("SP");
        Map<String, Double> reliever = ScoreCalculator.conditionProbabilitiesForPosition("RP");

        assertThat(starter).containsEntry("1_3회", 0.54);
        assertThat(starter).containsEntry("4_6회", 0.34);
        assertThat(starter).containsEntry("7_9회", 0.12);
        assertThat(starter).containsEntry("6회까지", 0.88);
        assertThat(starter).containsEntry("7회까지", 0.94);
        assertThat(reliever).containsEntry("1_3회", 0.00);
        assertThat(reliever).containsEntry("7_9회", 0.79);
    }

    @Test
    void appliesPlateAppearanceProbabilitiesByBattingOrderGroup() {
        Map<String, Double> defaultOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER");
        Map<String, Double> topOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 1);
        Map<String, Double> middleOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4);
        Map<String, Double> lowerOrder = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 8);

        assertThat(defaultOrder.get("타석1")).isCloseTo(1.0 / 3.595, within(0.0001));
        assertThat(middleOrder.get("타석2")).isCloseTo(1.0 / 3.595, within(0.0001));
        assertThat(middleOrder.get("타석3")).isCloseTo(0.90 / 3.595, within(0.0001));
        assertThat(middleOrder.get("타석4_7")).isCloseTo(0.695 / 3.595, within(0.0001));
        assertThat(topOrder.get("타석7")).isCloseTo(0.01 / 3.96, within(0.0001));
        assertThat(lowerOrder.get("타석7")).isEqualTo(0.0);
    }

    @Test
    void combinesPositionGateAndInningUntilConditionsAsMultiplication() {
        ScoreSkill skill = scoreSkill("G_055", "퀄리티 스타트",
                effect("파워", "포지션_SP+6회까지", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result starter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );
        ScoreCalculator.Result batter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );

        assertThat(starter.total()).isEqualTo(8.80);
        assertThat(batter.total()).isEqualTo(0.00);
    }

    @Test
    void calculatesPitcherOverpaceInningTierExpectedValueForStarter() {
        ScoreSkill skill = scoreSkill("G_075", "오버페이스",
                effect("구위", "1_3회", "7"),
                effect("구위", "4_6회", "5"),
                effect("구위", "7_9회", "2")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );

        assertThat(result.total()).isEqualTo(5.72);
    }

    @Test
    void calculatesBatterOverpacePlateTierExpectedValueForMiddleOrder() {
        ScoreSkill skill = scoreSkill("G_034", "오버페이스",
                effect("파워", "타석1", "8"),
                effect("파워", "타석2", "6"),
                effect("파워", "타석3", "4"),
                effect("파워", "타석4_7", "2")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4),
                Map.of()
        );

        assertThat(result.total()).isEqualTo(5.28);
    }

    @Test
    void appliesRoleBasedNineBatterDurationProbabilities() {
        ScoreSkill skill = scoreSkill("M_029", "스탠드아웃",
                effect("구위", "등판후9타자", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result batter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );
        ScoreCalculator.Result starter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );
        ScoreCalculator.Result reliever = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
                Map.of()
        );
        ScoreCalculator.Result closer = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("CP"),
                Map.of()
        );

        assertThat(batter.total()).isEqualTo(0.00);
        assertThat(starter.total()).isEqualTo(4.50);
        assertThat(reliever.total()).isEqualTo(9.50);
        assertThat(closer.total()).isEqualTo(10.00);
    }

    @Test
    void exposesRoleBasedShortBatterDurationProbabilities() {
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("BATTER"))
                .containsEntry("등판후3타자", 0.00)
                .containsEntry("등판후4타자", 0.00);
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("SP"))
                .containsEntry("등판후3타자", 0.15)
                .containsEntry("등판후4타자", 0.20);
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("RP"))
                .containsEntry("등판후3타자", 0.65)
                .containsEntry("등판후4타자", 0.80);
        assertThat(ScoreCalculator.conditionProbabilitiesForPosition("CP"))
                .containsEntry("등판후3타자", 0.90)
                .containsEntry("등판후4타자", 1.00);
    }

    @Test
    void appliesSecondPlateAppearanceDurationProbabilitiesByBattingOrder() {
        ScoreSkill skill = scoreSkill("M_032", "스탠드아웃",
                effect("파워", "두번째타석까지", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result defaultOrder = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );
        ScoreCalculator.Result topOrder = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 1),
                Map.of()
        );
        ScoreCalculator.Result middleOrder = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4),
                Map.of()
        );
        ScoreCalculator.Result lowerOrder = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 8),
                Map.of()
        );

        assertThat(defaultOrder.total()).isEqualTo(5.50);
        assertThat(topOrder.total()).isEqualTo(5.00);
        assertThat(middleOrder.total()).isEqualTo(5.50);
        assertThat(lowerOrder.total()).isEqualTo(5.80);
    }

    @Test
    void appliesStatComparisonProbabilitiesUsingDocumentedGutsTable() {
        ScoreSkill skill = scoreSkill("M_041", "파워 피처",
                effect("파워", "인내<구속", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result batter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );
        ScoreCalculator.Result starter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );
        ScoreCalculator.Result reliever = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
                Map.of()
        );
        ScoreCalculator.Result closer = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("CP"),
                Map.of()
        );

        assertThat(batter.total()).isEqualTo(2.00);
        assertThat(starter.total()).isEqualTo(8.00);
        assertThat(reliever.total()).isEqualTo(9.50);
        assertThat(closer.total()).isEqualTo(9.00);
    }

    @Test
    void appliesSameRoleBasedProbabilityToOvrUnderdogAndGutsTokens() {
        assertThat(totalForCondition("BATTER", "OVR열세")).isEqualTo(2.00);
        assertThat(totalForCondition("SP", "OVR열세")).isEqualTo(8.00);
        assertThat(totalForCondition("RP", "OVR열세")).isEqualTo(9.50);
        assertThat(totalForCondition("CP", "OVR열세")).isEqualTo(9.00);
        assertThat(totalForCondition("RP", "패기")).isEqualTo(9.50);
        assertThat(totalForCondition("CP", "구속>인내")).isEqualTo(9.00);
    }

    @Test
    void appliesR19StaticConditionProbabilities() {
        assertThat(totalForCondition("BATTER", "덱스코어열세")).isEqualTo(5.00);
        assertThat(totalForCondition("BATTER", "홈런3이상")).isEqualTo(0.05);
    }

    @Test
    void exposesConditionalNormalSkillProbabilityTokens() {
        Map<String, Double> probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER");

        assertThat(probabilities)
                .containsEntry("대타첫타석", 0.0)
                .containsEntry("교체후첫타자", 0.25);
    }

    @Test
    void appliesR19StatComparisonConditionProbabilities() {
        assertThat(totalForCondition("BATTER", "구위>파워")).isEqualTo(3.50);
        assertThat(totalForCondition("BATTER", "선구>제구")).isEqualTo(6.50);
    }

    @Test
    void bornToHitStatComparisonIsAlwaysApplied() {
        ScoreSkill skill = scoreSkill("M_044", "본 투 히트",
                effect("파워", "파워정확합>주루수비합", "12")
        );
        ScoreCalculator calculator = new ScoreCalculator();
        Map<String, Double> probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER");

        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                probabilities,
                Map.of("파워", 130.0, "정확", 125.0, "주루", 100.0, "수비", 100.0)
        ).total()).isEqualTo(12.00);
        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                probabilities,
                Map.of("파워", 100.0, "정확", 100.0, "주루", 130.0, "수비", 125.0)
        ).total()).isEqualTo(12.00);
        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                probabilities,
                Map.of()
        ).total()).isEqualTo(12.00);
    }

    @Test
    void appliesR19PitchCommandOverDisciplineProbabilityByRole() {
        assertThat(totalForCondition("BATTER", "제구>선구")).isEqualTo(4.50);
        assertThat(totalForCondition("SP", "제구>선구")).isEqualTo(4.50);
        assertThat(totalForCondition("RP", "제구>선구")).isEqualTo(1.00);
        assertThat(totalForCondition("CP", "제구>선구")).isEqualTo(2.50);
    }

    @Test
    void appliesOpponentGradeAdvantageProbabilityByOwnCardType() {
        assertThat(totalForCondition("BATTER", "상대등급우세")).isEqualTo(0.50);
        assertThat(totalForCondition("BATTER", "상대등급우세", "MOMENT")).isEqualTo(4.00);
        assertThat(totalForCondition("BATTER", "상대등급우세", "SIGNATURE")).isEqualTo(2.00);
        assertThat(totalForCondition("BATTER", "상대등급우세", "WBC")).isEqualTo(1.50);
        assertThat(totalForCondition("BATTER", "상대등급우세", "SIGNATURE_BLACK")).isEqualTo(0.50);
        assertThat(totalForCondition("BATTER", "상대등급우세", "WBC_SIGNATURE_BLACK")).isEqualTo(0.20);
        assertThat(totalForCondition("BATTER", "상대등급우세", "HOF")).isEqualTo(0.00);
    }

    @Test
    void opponentGradeAdvantageProbabilitiesAreStrictlyDecreasingUpTheLadder() {
        Map<String, Double> table = ScoreCalculator.getOpponentGradeAdvantageProbabilitiesByCardType();
        List<String> lowToHigh = List.of("MOMENT", "NORMAL", "WBC", "BLACK", "WBC_BLACK", "HOF");

        for (int i = 1; i < lowToHigh.size(); i++) {
            assertThat(table.get(lowToHigh.get(i))).isLessThan(table.get(lowToHigh.get(i - 1)));
        }
        assertThat(table.get("HOF")).isEqualTo(0.00);
    }

    @Test
    void exposesR19TokensInDefaultBatterConditionProbabilities() {
        Map<String, Double> probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER");

        assertThat(probabilities)
                .containsEntry("덱스코어열세", 0.5)
                .containsEntry("홈런3이상", 0.005)
                .containsEntry("구위>파워", 0.35)
                .containsEntry("선구>제구", 0.65)
                .containsEntry("제구>선구", 0.45)
                .containsEntry("상대등급우세", 0.05);
    }

    @Test
    void appliesMaestroCumulativeAverageStackByPitcherRole() {
        ScoreSkill skill = scoreSkill("M_042", "마에스트로",
                effect("구위", "마에스트로누적", "12")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        ).total()).isEqualTo(0.00);
        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        ).total()).isEqualTo(7.41);
        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
                Map.of()
        ).total()).isEqualTo(1.50);
        assertThat(calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("CP"),
                Map.of()
        ).total()).isEqualTo(1.00);
    }

    @Test
    void calculatesProportionalEffectsFromUserStats() {
        ScoreSkill skill = scoreSkill("G_057", "하드 트레이닝",
                proportionalEffect("구위", "ALWAYS", "0.05", "지구력")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.20),
                Map.of(),
                Map.of("지구력", 200.0)
        );

        assertThat(result.total()).isEqualTo(12.00);
        assertThat(result.perStat()).containsEntry("구위", 10.00);
    }

    @Test
    void sumsCompositeBaseStatForProportionalEffects() {
        ScoreSkill skill = scoreSkill("G_068", "핀포인트 컨트롤",
                proportionalEffect("구위", "ALWAYS", "0.02", "변화+제구")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("구위", 1.20),
                Map.of(),
                Map.of("변화", 100.0, "제구", 50.0)
        );

        // 기준값 = 변화(100) + 제구(50) = 150, value = floor(150 x 0.02) = 3
        assertThat(result.perStat()).containsEntry("구위", 3.00);
        assertThat(result.total()).isEqualTo(3.60);
        ScoreCalculator.EffectBreakdown breakdown = result.perSkill().get(0).breakdown().get(0);
        assertThat(breakdown.baseStat()).isEqualTo("변화+제구");
        assertThat(breakdown.baseValue()).isEqualTo(150.0);
        assertThat(breakdown.value()).isEqualTo(3.0);
    }

    @Test
    void rejectsUnknownConditionTokensInsteadOfFallingBackToAlwaysActive() {
        ScoreSkill skill = scoreSkill("X_001", "Unknown",
                effect("파워", "미정의조건", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        assertThatThrownBy(() -> calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown condition");
    }

    @Test
    void gatesPositionConditionsByRequestedPosition() {
        ScoreSkill skill = scoreSkill("G_048", "Ace Killer",
                effect("정확", "포지션_SP", "10"),
                effect("선구", "포지션_SP+포지션_RP_CP", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result batter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("정확", 1.0, "선구", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );
        ScoreCalculator.Result starter = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("정확", 1.0, "선구", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP"),
                Map.of()
        );
        ScoreCalculator.Result reliever = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("정확", 1.0, "선구", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP"),
                Map.of()
        );

        assertThat(batter.total()).isEqualTo(0.00);
        assertThat(starter.total()).isEqualTo(20.00);
        assertThat(reliever.total()).isEqualTo(10.00);
    }

    @Test
    void appliesDocumentedDefaultConditionProbabilities() {
        ScoreSkill skill = scoreSkill("B_001", "Condition Defaults",
                effect("파워", "초구", "10"),
                effect("정확", "타순1_2", "10"),
                effect("선구", "리드", "10"),
                effect("인내", "리드아님", "10"),
                effect("주루", "스트라이크타격", "10"),
                effect("수비", "2스트라이크", "10"),
                effect("발사각", "발사각조건", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of(
                        "파워", 1.0,
                        "정확", 1.0,
                        "선구", 1.0,
                        "인내", 1.0,
                        "주루", 1.0,
                        "수비", 1.0,
                        "발사각", 1.0
                ),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of()
        );

        // perStat 은 순수 증가량(value=10)만 표시하므로, 조건확률 반영은 가중 총점(total)으로 검증한다.
        assertThat(result.perStat()).containsEntry("파워", 10.00);
        assertThat(result.perStat()).containsEntry("정확", 10.00);
        assertThat(result.total()).isEqualTo(26.22);
    }

    @Test
    void usesBattingOrderGateWhenBattingOrderIsProvided() {
        Map<String, Double> probabilities = ScoreCalculator.conditionProbabilitiesForPosition("BATTER", 4);

        assertThat(probabilities).containsEntry("타순1", 0.0);
        assertThat(probabilities).containsEntry("타순1_2", 0.0);
        assertThat(probabilities).containsEntry("타순3_4_5", 1.0);
        assertThat(probabilities).containsEntry("타순4_5", 1.0);
        assertThat(probabilities).containsEntry("타순6_9", 0.0);
    }

    @Test
    void floorsStatIncreaseBeforeApplyingStatWeight() {
        ScoreSkill skill = scoreSkill("G_038", "결속력",
                proportionalEffect("파워", "ALWAYS", "0.015", "스페셜덱")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.2),
                Map.of(),
                Map.of("스페셜덱", 500.0)
        );

        assertThat(result.total()).isEqualTo(8.40);
        assertThat(result.perStat()).containsEntry("파워", 7.00);
    }

    @Test
    void usesDeckScoreDefaultForProportionalDeckEffects() {
        ScoreSkill skill = scoreSkill("G_038", "결속력",
                proportionalEffect("파워", "ALWAYS", "0.01", "스페셜덱")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.2),
                Map.of(),
                Map.of()
        );

        assertThat(result.total()).isEqualTo(6.00);
        assertThat(result.perStat()).containsEntry("파워", 5.00);
    }

    @Test
    void exposesEffectLevelBreakdownForFormulaDisplay() {
        ScoreSkill skill = scoreSkill("G_038", "결속력",
                proportionalEffect("파워", "리드", "0.01", "스페셜덱")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result result = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.2),
                ScoreCalculator.conditionProbabilitiesForPosition("BATTER"),
                Map.of("스페셜덱", 500.0)
        );

        assertThat(result.perSkill().get(0).breakdown()).hasSize(1);
        ScoreCalculator.EffectBreakdown breakdown = result.perSkill().get(0).breakdown().get(0);
        assertThat(breakdown.stat()).isEqualTo("파워");
        assertThat(breakdown.condition()).isEqualTo("리드");
        assertThat(breakdown.baseStat()).isEqualTo("스페셜덱");
        assertThat(breakdown.baseValue()).isEqualTo(500.0);
        assertThat(breakdown.value()).isEqualTo(5.0);
        assertThat(breakdown.weight()).isEqualTo(1.2);
        assertThat(breakdown.conditionProbability()).isEqualTo(0.37);
        assertThat(breakdown.subtotal()).isEqualTo(2.22);
    }

    @Test
    void verifiesSlotGateResolverValues() {
        Map<String, Double> sp1 = ScoreCalculator.conditionProbabilitiesForPosition("SP", null, 1);
        assertThat(sp1).containsEntry("선발1", 1.0);
        assertThat(sp1).containsEntry("선발1_2", 1.0);
        assertThat(sp1).containsEntry("선발3_4", 0.0);
        assertThat(sp1).containsEntry("선발3_4_5", 0.0);
        assertThat(sp1).containsEntry("선발4_5", 0.0);
        assertThat(sp1).containsEntry("중계3_4_5", 0.0);

        Map<String, Double> sp4 = ScoreCalculator.conditionProbabilitiesForPosition("SP", null, 4);
        assertThat(sp4).containsEntry("선발1", 0.0);
        assertThat(sp4).containsEntry("선발1_2", 0.0);
        assertThat(sp4).containsEntry("선발3_4", 1.0);
        assertThat(sp4).containsEntry("선발3_4_5", 1.0);
        assertThat(sp4).containsEntry("선발4_5", 1.0);

        Map<String, Double> rp4 = ScoreCalculator.conditionProbabilitiesForPosition("RP", null, 4);
        assertThat(rp4).containsEntry("선발3_4_5", 0.0);
        assertThat(rp4).containsEntry("중계3_4_5", 1.0);

        Map<String, Double> spNull = ScoreCalculator.conditionProbabilitiesForPosition("SP", null, null);
        assertThat(spNull).containsEntry("선발1", 0.0);
    }

    @Test
    void verifiesOrGroupLogicForPitcherSlots() {
        ScoreSkill skill = scoreSkill("G_052", "라이징 스타",
                effect("파워", "선발3_4_5+중계3_4_5", "10")
        );
        ScoreCalculator calculator = new ScoreCalculator();

        ScoreCalculator.Result spSlot4 = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP", null, 4),
                Map.of()
        );
        assertThat(spSlot4.total()).isEqualTo(10.00);

        ScoreCalculator.Result rpSlot4 = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("RP", null, 4),
                Map.of()
        );
        assertThat(rpSlot4.total()).isEqualTo(10.00);

        ScoreCalculator.Result spSlot1 = calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition("SP", null, 1),
                Map.of()
        );
        assertThat(spSlot1.total()).isEqualTo(0.00);
    }

    private ScoreSkill scoreSkill(String skillKey, String name, ScoreEffect... effects) {
        ScoreSkill skill = ScoreSkill.builder()
                .skillKey(skillKey)
                .cardType("NORMAL")
                .position("BATTER")
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

    private ScoreEffect proportionalEffect(String stat, String condition, String values, String baseStat) {
        return ScoreEffect.builder()
                .stat(stat)
                .condition(condition)
                .values(values)
                .baseStat(baseStat)
                .build();
    }

    private double totalForCondition(String position, String condition) {
        return totalForCondition(position, condition, null);
    }

    private double totalForCondition(String position, String condition, String cardType) {
        ScoreSkill skill = scoreSkill("G_007", "패기", effect("파워", condition, "10"));
        ScoreCalculator calculator = new ScoreCalculator();

        return calculator.calculate(
                List.of(new ScoreCalculator.Selection(skill, 1)),
                Map.of("파워", 1.0),
                ScoreCalculator.conditionProbabilitiesForPosition(position, null, null, cardType),
                Map.of()
        ).total();
    }
}
