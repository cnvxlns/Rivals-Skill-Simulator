package com.example.skillsim.service;

import com.example.skillsim.enums.Handedness;
import com.example.skillsim.model.ScoreEffect;
import com.example.skillsim.model.ScoreSkill;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ScoreCalculator {

    public static double getDefaultUserStat() {
        return DEFAULT_USER_STAT;
    }

    public static double getDefaultDeckScore() {
        return DEFAULT_DECK_SCORE;
    }

    public static Map<String, double[]> getInningWeightsByRole() {
        Map<String, double[]> copy = new LinkedHashMap<>();
        INNING_WEIGHTS_BY_ROLE.forEach((k, v) -> copy.put(k, v.clone()));
        return Collections.unmodifiableMap(copy);
    }

    public static Map<String, Double> getStaticConditionProbabilities() {
        return STATIC_CONDITION_PROBABILITIES;
    }

    public static Map<String, Double> getPlateSituationProbabilities() {
        return PLATE_SITUATION_PROBABILITIES;
    }

    public static Map<String, Double> getBattingOrderDefaultProbabilities() {
        return BATTING_ORDER_DEFAULT_PROBABILITIES;
    }

    public static Map<String, Double> getGameStateProbabilities() {
        return GAME_STATE_PROBABILITIES;
    }

    public static Map<String, Double> getModeProbabilities() {
        return MODE_PROBABILITIES;
    }

    public static Map<String, Double> getLaunchAngleProbabilities() {
        return LAUNCH_ANGLE_PROBABILITIES;
    }

    public static Map<String, Double> getNineBatterDurationProbabilitiesByRole() {
        return NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE;
    }

    public static Map<String, Double> getGutsProbabilitiesByRole() {
        return GUTS_PROBABILITIES_BY_ROLE;
    }

    public static Map<String, Double> getPatienceBelowVelocityProbabilitiesByRole() {
        return PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE;
    }

    public static Map<String, Double> getMaestroCumulativeProbabilitiesByRole() {
        return MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE;
    }

    public static Map<String, Double> getOpponentGradeAdvantageProbabilitiesByCardType() {
        return OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE;
    }

    public static double[] getTopOrderPlateAppearanceReach() {
        return TOP_ORDER_PLATE_APPEARANCE_REACH.clone();
    }

    public static double[] getMiddleOrderPlateAppearanceReach() {
        return MIDDLE_ORDER_PLATE_APPEARANCE_REACH.clone();
    }

    public static double[] getLowerOrderPlateAppearanceReach() {
        return LOWER_ORDER_PLATE_APPEARANCE_REACH.clone();
    }

    private static final double DEFAULT_USER_STAT = 120.0;
    private static final double DEFAULT_DECK_SCORE = 500.0;
    private static final String BATTER_OFFENSE_OVER_DEFENSE_CONDITION = "파워정확합>주루수비합";

    private static final Map<String, double[]> INNING_WEIGHTS_BY_ROLE = Map.of(
            "SP", new double[]{0.190, 0.180, 0.170, 0.130, 0.110, 0.100, 0.060, 0.040, 0.020},
            "BATTER", new double[]{0.150, 0.140, 0.130, 0.120, 0.110, 0.100, 0.090, 0.080, 0.080},
            "RP", new double[]{0.000, 0.000, 0.000, 0.000, 0.030, 0.180, 0.340, 0.350, 0.100},
            "CP", new double[]{0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.100, 0.900}
    );

    private static final Map<String, Double> STATIC_CONDITION_PROBABILITIES = Map.ofEntries(
            Map.entry("ALWAYS", 1.0),
            Map.entry("홈", 0.3),
            Map.entry("원정", 0.7),
            Map.entry("주자있음", 0.4),
            Map.entry("주자없음", 0.6),
            Map.entry("주자1명", 0.25),
            Map.entry("주자2명이상", 0.25),
            Map.entry("주자2루이상", 0.25),
            Map.entry("주자3루", 0.03),
            Map.entry("OVR열세", 0.5),
            Map.entry("OVR우세", 0.5),
            Map.entry("덱스코어열세", 0.5),
            Map.entry("홈런3이상", 0.005),
            Map.entry("좌투상대", 0.3),
            Map.entry("우투상대", 0.7),
            Map.entry("좌타상대", 0.4),
            Map.entry("우타상대", 0.6),
            Map.entry("직구상대", 0.45),
            Map.entry("속구선택", 0.45),
            Map.entry("변화구상대", 0.55),
            Map.entry("변화구선택", 0.55),
            Map.entry("스윗스팟", 0.3),
            Map.entry("당겨치기", 0.35),
            Map.entry("밀어치기", 0.35),
            Map.entry("높은공", 0.333),
            Map.entry("낮은공", 0.333),
            Map.entry("대타첫타석", 0.0), // 주전 라인업 기준 대타 출전 없음
            Map.entry("교체후첫타자", 0.25), // 계투 상대 타자 중 첫 타자 근사
            Map.entry("풀카운트", 0.048),
            Map.entry("2아웃", 0.333)
    );

    private static final Map<String, Double> PLATE_SITUATION_PROBABILITIES = Map.of(
            "초구", 0.30,
            "스트라이크타격", 0.55,
            "2스트라이크", 0.35
    );

    private static final Map<String, Double> BATTING_ORDER_DEFAULT_PROBABILITIES = Map.ofEntries(
            Map.entry("타순1", 0.111),
            Map.entry("타순1_2", 0.222),
            Map.entry("타순2_3", 0.222),
            Map.entry("타순3_4_5", 0.333),
            Map.entry("타순4_5", 0.222),
            Map.entry("타순6_9", 0.444),
            Map.entry("타순8_9", 0.222)
    );

    private static final Map<String, Double> GAME_STATE_PROBABILITIES = Map.ofEntries(
            Map.entry("리드", 0.37),
            Map.entry("리드아님", 0.63),
            Map.entry("비김또는리드", 0.63),
            Map.entry("비김또는열세", 0.63)
    );

    private static final Map<String, Double> MODE_PROBABILITIES = Map.ofEntries(
            Map.entry("모드_랭킹대전", 1.0),
            Map.entry("모드_랭킹토너먼트", 0.0),
            Map.entry("모드_라이브매치", 0.0),
            Map.entry("모드_리그", 0.0),
            Map.entry("모드_클럽", 0.0),
            Map.entry("모드_타점배틀", 0.0),
            Map.entry("모드_랭킹슬러거", 0.0)
    );

    private static final Map<String, Double> LAUNCH_ANGLE_PROBABILITIES = Map.ofEntries(
            Map.entry("발사각조건", 0.20),
            Map.entry("발사각10이상", 0.50),
            Map.entry("발사각14이하", 0.50)
    );

    private static final Map<String, Double> NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.00,
            "SP", 0.45,
            "RP", 0.95,
            "CP", 1.00
    );

    // 보직별 평균 상대 타자 수 대비 해당 타자 수 비중 근사(등판후9타자와 동일 방법론).
    private static final Map<String, Double> THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.00,
            "SP", 0.15,
            "RP", 0.65,
            "CP", 0.90
    );

    private static final Map<String, Double> FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.00,
            "SP", 0.20,
            "RP", 0.80,
            "CP", 1.00
    );

    private static final Map<String, Double> GUTS_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.20,
            "SP", 0.80,
            "RP", 0.95,
            "CP", 0.90
    );

    // "인내<구속"(투수 구속 > 상대 타자 인내)은 GUTS_PROBABILITIES_BY_ROLE(패기: 상대가 우세할 확률)의 반대 사건이므로 1 - 패기확률로 산출.
    private static final Map<String, Double> PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.80,
            "SP", 0.20,
            "RP", 0.05,
            "CP", 0.10
    );

    private static final Map<String, Double> MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE = Map.of(
            "BATTER", 0.0,
            "SP", maestroAverageActiveStack(17) / 12.0,
            "RP", maestroAverageActiveStack(4) / 12.0,
            "CP", maestroAverageActiveStack(3) / 12.0
    );

    // 도전정신(상대등급우세): 자기 카드 등급 기준 P(상대 선수 등급 > 내 등급).
    // 상대 라인업이 프라임/시그니처/모먼트/HOF 위주라는 메타 가정에서 산출한 값 (agy×3+codex×3 2라운드 토론 합의).
    // WBC 계열은 일반 계열의 리스킨(동일 등급)이므로 WBC=NORMAL(프라임/시그니처), WBC_BLACK=BLACK과 같은 값을 사용한다.
    // SUPREME_MOMENT(슈프림 모먼트)는 모먼트<슈프림모먼트<시그니처 순서를 반영해 MOMENT와 NORMAL의 중간값을 사용한다.
    private static final String DEFAULT_CARD_TYPE = "BLACK";
    private static final Map<String, Double> OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE = Map.of(
            "MOMENT", 0.40,
            "SUPREME_MOMENT", 0.30,
            "NORMAL", 0.20,
            "WBC", 0.20,
            "BLACK", 0.05,
            "WBC_BLACK", 0.05,
            "HOF", 0.00
    );

    private static final double[] TOP_ORDER_PLATE_APPEARANCE_REACH = new double[]{1.0, 1.0, 0.95, 0.70, 0.25, 0.05, 0.01};
    private static final double[] MIDDLE_ORDER_PLATE_APPEARANCE_REACH = new double[]{1.0, 1.0, 0.90, 0.55, 0.12, 0.02, 0.005};
    private static final double[] LOWER_ORDER_PLATE_APPEARANCE_REACH = new double[]{1.0, 0.95, 0.80, 0.40, 0.06, 0.01, 0.00};

    private static final List<ConditionResolver> CONDITION_RESOLVERS = List.of(
            new StaticProbabilityResolver(STATIC_CONDITION_PROBABILITIES),
            new StaticProbabilityResolver(PLATE_SITUATION_PROBABILITIES),
            new BattingOrderResolver(),
            new StaticProbabilityResolver(GAME_STATE_PROBABILITIES),
            new StaticProbabilityResolver(MODE_PROBABILITIES),
            new StaticProbabilityResolver(LAUNCH_ANGLE_PROBABILITIES),
            new DurationResolver(),
            new MaestroCumulativeResolver(),
            new StatComparisonResolver(),
            new CardGradeResolver(),
            new PositionGateResolver(),
            new HandednessGateResolver(),
            new SlotGateResolver(),
            new InningResolver(),
            new InningRangeResolver(),
            new PlateAppearanceResolver()
    );

    private static final Map<String, Double> DEFAULT_CONDITION_PROBABILITIES = buildConditionProbabilities("BATTER", null, null, null, null, null);

    public Result calculate(List<Selection> selections, Map<String, Double> statWeights) {
        return calculate(selections, statWeights, DEFAULT_CONDITION_PROBABILITIES, Map.of());
    }

    public Result calculate(
            List<Selection> selections,
            Map<String, Double> statWeights,
            Map<String, Double> conditionProbabilities
    ) {
        return calculate(selections, statWeights, conditionProbabilities, Map.of());
    }

    public Result calculate(
            List<Selection> selections,
            Map<String, Double> statWeights,
            Map<String, Double> conditionProbabilities,
            Map<String, Double> userStats
    ) {
        Map<String, Double> totalPerStat = new LinkedHashMap<>();
        List<SkillScore> perSkill = new ArrayList<>();
        Map<String, Double> safeUserStats = userStats == null ? Map.of() : userStats;
        double weightedTotal = 0.0;

        for (Selection selection : selections) {
            ScoreSkill skill = selection.skill();
            Map<String, Double> skillPerStat = new LinkedHashMap<>();
            List<EffectBreakdown> breakdown = new ArrayList<>();
            double skillScore = 0.0;

            for (ScoreEffect effect : skill.getEffects()) {
                double weight = statWeights.getOrDefault(effect.getStat(), 0.0);
                double rawValue = valueAt(effect.getValues(), selection.level());
                double value = rawValue;
                String baseStat = null;
                Double baseValue = null;
                if (effect.getBaseStat() != null && !effect.getBaseStat().isBlank()) {
                    baseStat = effect.getBaseStat();
                    baseValue = userStatValue(safeUserStats, effect.getBaseStat());
                    value = baseValue * rawValue;
                }
                value = Math.floor(value);
                double conditionProbability = conditionProbability(effect.getCondition(), conditionProbabilities);

                double contribution = weight * value * conditionProbability;

                // perStat 은 "스킬로 증가한 스탯 절대치"를 표시하므로 weight·조건확률을 제외한 순수 증가량(value)만 누적한다.
                mergeRounded(skillPerStat, effect.getStat(), value);
                mergeRounded(totalPerStat, effect.getStat(), value);
                skillScore += contribution;
                weightedTotal += contribution;
                breakdown.add(new EffectBreakdown(
                        effect.getStat(),
                        effect.getCondition(),
                        round(weight),
                        round(value),
                        round(conditionProbability),
                        round(contribution),
                        baseStat,
                        baseValue == null ? null : round(baseValue),
                        round(rawValue)
                ));
            }

            perSkill.add(new SkillScore(
                    skill.getSkillKey(),
                    skill.getName(),
                    round(skillScore),
                    roundedCopy(skillPerStat),
                    breakdown
            ));
        }

        return new Result(round(weightedTotal), perSkill, roundedCopy(totalPerStat));
    }

    public static Map<String, Double> conditionProbabilitiesForPosition(String position) {
        return conditionProbabilitiesForPosition(position, null, null);
    }

    public static Map<String, Double> conditionProbabilitiesForPosition(String position, Integer battingOrder) {
        return conditionProbabilitiesForPosition(position, battingOrder, null);
    }

    public static Map<String, Double> conditionProbabilitiesForPosition(String position, Integer battingOrder, Integer pitcherSlot) {
        return conditionProbabilitiesForPosition(position, battingOrder, pitcherSlot, null);
    }

    public static Map<String, Double> conditionProbabilitiesForPosition(
            String position,
            Integer battingOrder,
            Integer pitcherSlot,
            String cardType
    ) {
        return conditionProbabilitiesForPosition(position, battingOrder, pitcherSlot, cardType, null, null);
    }

    /**
     * 투/타 방향까지 반영한 조건 확률표.
     *
     * <p>방향이 주어지지 않으면 우완/우타로 간주한다. 좌완 전용 절(예: 빅 유닛의 "좌완 선발로 등판 시")이
     * 방향 미상일 때 발동하지 않도록 하기 위한 보수적 기본값이다.
     */
    public static Map<String, Double> conditionProbabilitiesForPosition(
            String position,
            Integer battingOrder,
            Integer pitcherSlot,
            String cardType,
            Handedness throwHand,
            Handedness batHand
    ) {
        return buildConditionProbabilities(position, battingOrder, pitcherSlot, cardType, throwHand, batHand);
    }

    private static Map<String, Double> buildConditionProbabilities(
            String position,
            Integer battingOrder,
            Integer pitcherSlot,
            String cardType,
            Handedness throwHand,
            Handedness batHand
    ) {
        ConditionContext context = new ConditionContext(
                SkillRules.normalizePosition(position),
                SkillRules.roleForPosition(position),
                battingOrder,
                pitcherSlot,
                cardType,
                throwHand == null ? Handedness.RIGHT : throwHand,
                batHand == null ? Handedness.RIGHT : batHand
        );
        Map<String, Double> probabilities = new HashMap<>();
        CONDITION_RESOLVERS.forEach(resolver -> resolver.apply(probabilities, context));
        return probabilities;
    }

    private interface ConditionResolver {
        void apply(Map<String, Double> probabilities, ConditionContext context);
    }

    private record ConditionContext(
            String normalizedPosition,
            String role,
            Integer battingOrder,
            Integer pitcherSlot,
            String cardType,
            Handedness throwHand,
            Handedness batHand
    ) {
    }

    private record StaticProbabilityResolver(Map<String, Double> probabilities) implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> target, ConditionContext context) {
            target.putAll(probabilities);
        }
    }

    /** 선수 본인의 투/타 방향 게이트. 확률이 아니라 참/거짓이다. */
    private static final class HandednessGateResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            Handedness thrown = context.throwHand();
            Handedness bats = context.batHand();
            probabilities.put("좌완", thrown == Handedness.LEFT ? 1.0 : 0.0);
            probabilities.put("우완", thrown == Handedness.RIGHT ? 1.0 : 0.0);
            // 스위치 타자는 좌/우 양쪽 상황을 모두 만족한다.
            probabilities.put("좌타", bats == Handedness.LEFT || bats == Handedness.SWITCH ? 1.0 : 0.0);
            probabilities.put("우타", bats == Handedness.RIGHT || bats == Handedness.SWITCH ? 1.0 : 0.0);
            probabilities.put("스위치타", bats == Handedness.SWITCH ? 1.0 : 0.0);
        }
    }

    private static final class PositionGateResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            String position = context.normalizedPosition();
            probabilities.put("포지션_SP", "SP".equals(position) ? 1.0 : 0.0);
            probabilities.put("포지션_RP_CP", Set.of("RP", "CP").contains(position) ? 1.0 : 0.0);
            probabilities.put("포지션_DH", "DH".equals(position) ? 1.0 : 0.0);
            probabilities.put("포지션_SS", "SS".equals(position) ? 1.0 : 0.0);
            probabilities.put("포지션_OF", Set.of("OF", "LF", "CF", "RF").contains(position) ? 1.0 : 0.0);
            probabilities.put("포지션_C", "C".equals(position) ? 1.0 : 0.0);
        }
    }

    private static final class SlotGateResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            String role = context.role();
            Integer slot = context.pitcherSlot();
            boolean isSP = "SP".equals(role);
            boolean isRP = "RP".equals(role);

            probabilities.put("선발1", (isSP && slot != null && slot == 1) ? 1.0 : 0.0);
            probabilities.put("선발1_2", (isSP && slot != null && (slot == 1 || slot == 2)) ? 1.0 : 0.0);
            probabilities.put("선발3_4", (isSP && slot != null && (slot == 3 || slot == 4)) ? 1.0 : 0.0);
            probabilities.put("선발3_4_5", (isSP && slot != null && (slot == 3 || slot == 4 || slot == 5)) ? 1.0 : 0.0);
            probabilities.put("선발4_5", (isSP && slot != null && (slot == 4 || slot == 5)) ? 1.0 : 0.0);
            probabilities.put("중계3_4_5", (isRP && slot != null && (slot == 3 || slot == 4 || slot == 5)) ? 1.0 : 0.0);
        }
    }

    private static final class BattingOrderResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            Integer battingOrder = context.battingOrder();
            if (battingOrder == null) {
                probabilities.putAll(BATTING_ORDER_DEFAULT_PROBABILITIES);
                return;
            }
            probabilities.put("타순1", battingOrder == 1 ? 1.0 : 0.0);
            probabilities.put("타순1_2", isBetween(battingOrder, 1, 2) ? 1.0 : 0.0);
            probabilities.put("타순2_3", isBetween(battingOrder, 2, 3) ? 1.0 : 0.0);
            probabilities.put("타순3_4_5", isBetween(battingOrder, 3, 5) ? 1.0 : 0.0);
            probabilities.put("타순4_5", isBetween(battingOrder, 4, 5) ? 1.0 : 0.0);
            probabilities.put("타순6_9", isBetween(battingOrder, 6, 9) ? 1.0 : 0.0);
            probabilities.put("타순8_9", isBetween(battingOrder, 8, 9) ? 1.0 : 0.0);
        }
    }

    private static final class DurationResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            probabilities.put("등판후9타자", NINE_BATTER_DURATION_PROBABILITIES_BY_ROLE.getOrDefault(context.role(), 0.0));
            probabilities.put("등판후3타자", THREE_BATTER_DURATION_PROBABILITIES_BY_ROLE.getOrDefault(context.role(), 0.0));
            probabilities.put("등판후4타자", FOUR_BATTER_DURATION_PROBABILITIES_BY_ROLE.getOrDefault(context.role(), 0.0));
            probabilities.put("두번째타석까지", secondPlateAppearanceProbability(context.battingOrder()));
        }

        private double secondPlateAppearanceProbability(Integer battingOrder) {
            if (battingOrder == null) {
                return 0.55;
            }
            if (isBetween(battingOrder, 1, 2)) {
                return 0.50;
            }
            if (isBetween(battingOrder, 3, 5)) {
                return 0.55;
            }
            if (isBetween(battingOrder, 6, 9)) {
                return 0.58;
            }
            return 0.55;
        }
    }

    private static final class StatComparisonResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            double probability = GUTS_PROBABILITIES_BY_ROLE.getOrDefault(
                    context.role(),
                    GUTS_PROBABILITIES_BY_ROLE.get("BATTER")
            );
            double patienceBelowVelocity = PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE.getOrDefault(
                    context.role(),
                    PATIENCE_BELOW_VELOCITY_PROBABILITIES_BY_ROLE.get("BATTER")
            );
            probabilities.put("OVR열세", probability);
            probabilities.put("패기", probability);
            probabilities.put("인내<구속", patienceBelowVelocity);
            probabilities.put("구속>인내", patienceBelowVelocity);
            probabilities.put("구위>파워", 0.35);
            probabilities.put("선구>제구", 0.65);
            probabilities.put(BATTER_OFFENSE_OVER_DEFENSE_CONDITION, 1.00);
            probabilities.put("제구>선구", switch (context.role()) {
                case "RP" -> 0.10;
                case "CP" -> 0.25;
                case "SP" -> 0.45;
                default -> 0.45;
            });
        }
    }

    private static final class CardGradeResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            String cardType = context.cardType() == null || context.cardType().isBlank()
                    ? DEFAULT_CARD_TYPE
                    : SkillRules.normalizeCardType(context.cardType());
            probabilities.put("상대등급우세", OPPONENT_GRADE_ADVANTAGE_PROBABILITIES_BY_CARD_TYPE.get(cardType));
        }
    }

    private static final class MaestroCumulativeResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            probabilities.put("마에스트로누적", MAESTRO_CUMULATIVE_PROBABILITIES_BY_ROLE.getOrDefault(context.role(), 0.0));
        }
    }

    private static final class InningResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            double[] inningWeights = INNING_WEIGHTS_BY_ROLE.getOrDefault(
                    context.role(),
                    INNING_WEIGHTS_BY_ROLE.get("BATTER")
            );
            for (int inning = 1; inning <= inningWeights.length; inning++) {
                double probability = 0.0;
                for (int idx = inning - 1; idx < inningWeights.length; idx++) {
                    probability += inningWeights[idx];
                }
                double rounded = roundStatic(probability);
                probabilities.put(inning + "회이후", rounded);
                probabilities.put(inning + "회", rounded);
            }
        }
    }

    private static final class InningRangeResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            double[] inningWeights = INNING_WEIGHTS_BY_ROLE.getOrDefault(
                    context.role(),
                    INNING_WEIGHTS_BY_ROLE.get("BATTER")
            );
            for (int start = 1; start <= inningWeights.length; start++) {
                double probability = 0.0;
                for (int end = start; end <= inningWeights.length; end++) {
                    probability += inningWeights[end - 1];
                    double rounded = roundStatic(probability);
                    probabilities.put(start + "_" + end + "회", rounded);
                    if (start == 1) {
                        probabilities.put(end + "회까지", rounded);
                    }
                }
            }
        }
    }

    private static final class PlateAppearanceResolver implements ConditionResolver {
        @Override
        public void apply(Map<String, Double> probabilities, ConditionContext context) {
            double[] reachProbabilities = plateAppearanceReachProbabilities(context.battingOrder());
            double totalReachProbability = 0.0;
            for (double reachProbability : reachProbabilities) {
                totalReachProbability += reachProbability;
            }

            for (int start = 1; start <= reachProbabilities.length; start++) {
                double normalizedProbability = reachProbabilities[start - 1] / totalReachProbability;
                probabilities.put("타석" + start, normalizedProbability);

                double rangeProbability = 0.0;
                for (int end = start; end <= reachProbabilities.length; end++) {
                    rangeProbability += reachProbabilities[end - 1] / totalReachProbability;
                    probabilities.put("타석" + start + "_" + end, rangeProbability);
                }
            }
        }

        private double[] plateAppearanceReachProbabilities(Integer battingOrder) {
            if (battingOrder == null) {
                return MIDDLE_ORDER_PLATE_APPEARANCE_REACH;
            }
            if (isBetween(battingOrder, 1, 2)) {
                return TOP_ORDER_PLATE_APPEARANCE_REACH;
            }
            if (isBetween(battingOrder, 3, 5)) {
                return MIDDLE_ORDER_PLATE_APPEARANCE_REACH;
            }
            if (isBetween(battingOrder, 6, 9)) {
                return LOWER_ORDER_PLATE_APPEARANCE_REACH;
            }
            return MIDDLE_ORDER_PLATE_APPEARANCE_REACH;
        }
    }

    private static boolean isBetween(int value, int min, int max) {
        return value >= min && value <= max;
    }

    private static double maestroAverageActiveStack(int expectedOuts) {
        if (expectedOuts <= 0) {
            return 0.0;
        }
        if (expectedOuts <= 12) {
            return (expectedOuts - 1) / 2.0;
        }
        return (66.0 + 12.0 * (expectedOuts - 12)) / expectedOuts;
    }

    double valueAt(String values, int level) {
        String[] tokens = values == null || values.isBlank() ? new String[]{"0"} : values.split("/");
        int clampedLevel = Math.max(1, Math.min(level, tokens.length));
        return Double.parseDouble(tokens[clampedLevel - 1].trim());
    }

    double conditionProbability(String condition, Map<String, Double> overrides) {
        if (condition == null || condition.isBlank() || condition.equalsIgnoreCase("ALWAYS")) {
            return 1.0;
        }
        Map<String, Double> safeOverrides = overrides == null ? Map.of() : overrides;

        String[] parts = condition.split("\\+");
        double nonModeProbability = 1.0;
        Double modeProbability = null;
        Double positionProbability = null;

        for (String rawPart : parts) {
            String part = rawPart.trim();
            double probability = resolveConditionPart(part, safeOverrides);
            if (part.startsWith("모드_")) {
                modeProbability = Math.max(modeProbability == null ? 0.0 : modeProbability, probability);
            } else if (part.startsWith("포지션_") || part.startsWith("선발") || part.startsWith("중계")) {
                positionProbability = Math.max(positionProbability == null ? 0.0 : positionProbability, probability);
            } else {
                nonModeProbability *= probability;
            }
        }

        return nonModeProbability
                * (modeProbability == null ? 1.0 : modeProbability)
                * (positionProbability == null ? 1.0 : positionProbability);
    }

    private double resolveConditionPart(String part, Map<String, Double> overrides) {
        if (overrides.containsKey(part)) {
            return overrides.get(part);
        }
        if (DEFAULT_CONDITION_PROBABILITIES.containsKey(part)) {
            return DEFAULT_CONDITION_PROBABILITIES.get(part);
        }
        throw new IllegalArgumentException("Unknown condition token: " + part);
    }

    private double userStatValue(Map<String, Double> userStats, String stat) {
        // 합산형 기준 스탯(예: "변화+제구")은 각 구성 스탯 값을 더해 기준값으로 사용한다.
        if (stat != null && stat.contains("+")) {
            double sum = 0.0;
            for (String part : stat.split("\\+")) {
                sum += userStatValue(userStats, part.trim());
            }
            return sum;
        }
        if (userStats.containsKey(stat)) {
            return userStats.get(stat);
        }
        return isDeckScoreStat(stat) ? DEFAULT_DECK_SCORE : DEFAULT_USER_STAT;
    }

    private boolean isDeckScoreStat(String stat) {
        return stat != null && stat.contains("덱");
    }

    private void mergeRounded(Map<String, Double> target, String stat, double contribution) {
        target.merge(stat, contribution, Double::sum);
        target.put(stat, round(target.get(stat)));
    }

    private Map<String, Double> roundedCopy(Map<String, Double> values) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        values.forEach((key, value) -> rounded.put(key, round(value)));
        return rounded;
    }

    private double round(double value) {
        return roundStatic(value);
    }

    private static double roundStatic(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record Selection(ScoreSkill skill, int level) {
    }

    public record Result(double total, List<SkillScore> perSkill, Map<String, Double> perStat) {
    }

    public record SkillScore(
            String skillKey,
            String name,
            double score,
            Map<String, Double> perStat,
            List<EffectBreakdown> breakdown
    ) {
    }

    public record EffectBreakdown(
            String stat,
            String condition,
            double weight,
            double value,
            double conditionProbability,
            double subtotal,
            String baseStat,
            Double baseValue,
            double rawValue
    ) {
    }
}
