package com.example.skillsim.service;

import com.example.skillsim.config.ScoreDataLoader;
import com.example.skillsim.dto.MethodologyResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MethodologyService {

    private final ScoreDataLoader scoreDataLoader;

    private static final Map<String, String> CONDITION_DESCRIPTION_KEYS = Map.ofEntries(
            Map.entry("ALWAYS", "condition_always"),
            Map.entry("홈", "condition_home"),
            Map.entry("원정", "condition_away"),
            Map.entry("주자있음", "condition_runners_on"),
            Map.entry("주자없음", "condition_runners_off"),
            Map.entry("주자1명", "condition_runner_1"),
            Map.entry("주자2명이상", "condition_runners_2_or_more"),
            Map.entry("주자2루이상", "condition_runner_2nd_or_higher"),
            Map.entry("주자3루", "condition_runner_3rd"),
            Map.entry("OVR열세", "condition_ovr_inferior"),
            Map.entry("OVR우세", "condition_ovr_superior"),
            Map.entry("좌투상대", "condition_vs_left_pitcher"),
            Map.entry("우투상대", "condition_vs_right_pitcher"),
            Map.entry("좌타상대", "condition_vs_left_batter"),
            Map.entry("우타상대", "condition_vs_right_batter"),
            Map.entry("직구상대", "condition_vs_fastball"),
            Map.entry("속구선택", "condition_fastball_selected"),
            Map.entry("변화구상대", "condition_vs_breakingball"),
            Map.entry("변화구선택", "condition_breakingball_selected"),
            Map.entry("스윗스팟", "condition_sweet_spot"),
            Map.entry("당겨치기", "condition_pull"),
            Map.entry("밀어치기", "condition_push"),
            Map.entry("높은공", "condition_high_ball"),
            Map.entry("낮은공", "condition_low_ball"),
            Map.entry("풀카운트", "condition_full_count"),
            Map.entry("2아웃", "condition_two_outs"),
            Map.entry("초구", "condition_first_pitch"),
            Map.entry("스트라이크타격", "condition_strike_hit"),
            Map.entry("1스트라이크", "condition_one_strike"),
            Map.entry("2스트라이크", "condition_two_strikes"),
            Map.entry("상대팀홈런3", "condition_opponent_three_homeruns"),
            Map.entry("이닝출루2인이상", "condition_inning_two_baserunners"),
            Map.entry("발사각조건", "condition_launch_angle"),
            Map.entry("발사각10이상", "condition_launch_angle_10_plus"),
            Map.entry("발사각14이하", "condition_launch_angle_14_minus"),
            Map.entry("타순1", "condition_batting_order_1"),
            Map.entry("타순1_2", "condition_batting_order_1_2"),
            Map.entry("타순2_3", "condition_batting_order_2_3"),
            Map.entry("타순3_4_5", "condition_batting_order_3_5"),
            Map.entry("타순4_5", "condition_batting_order_4_5"),
            Map.entry("타순6_9", "condition_batting_order_6_9"),
            Map.entry("타순8_9", "condition_batting_order_8_9"),
            Map.entry("리드", "condition_lead"),
            Map.entry("리드아님", "condition_not_leading"),
            Map.entry("비김또는리드", "condition_tie_or_lead"),
            Map.entry("비김또는열세", "condition_tie_or_behind"),
            Map.entry("모드_랭킹대전", "condition_mode_ranking_match"),
            Map.entry("모드_랭킹토너먼트", "condition_mode_ranking_tournament"),
            Map.entry("모드_라이브매치", "condition_mode_live_match"),
            Map.entry("모드_리그", "condition_mode_league"),
            Map.entry("모드_클럽", "condition_mode_club"),
            Map.entry("모드_타점배틀", "condition_mode_rbi_battle"),
            Map.entry("모드_랭킹슬러거", "condition_mode_ranking_slugger"),
            Map.entry("포지션_SP", "condition_gate_position_sp"),
            Map.entry("포지션_RP_CP", "condition_gate_position_rp_cp"),
            Map.entry("포지션_DH", "condition_gate_position_dh"),
            Map.entry("포지션_SS", "condition_gate_position_ss"),
            Map.entry("포지션_OF", "condition_gate_position_of"),
            Map.entry("포지션_C", "condition_gate_position_c"),
            Map.entry("선발1", "condition_gate_slot_sp_1"),
            Map.entry("선발1_2", "condition_gate_slot_sp_1_2"),
            Map.entry("선발3_4", "condition_gate_slot_sp_3_4"),
            Map.entry("선발3_4_5", "condition_gate_slot_sp_3_5"),
            Map.entry("선발4_5", "condition_gate_slot_sp_4_5"),
            Map.entry("중계3_4_5", "condition_gate_slot_rp_3_5")
    );

    private static final List<String> GATE_TOKENS = List.of(
            "포지션_SP", "포지션_RP_CP", "포지션_DH", "포지션_SS", "포지션_OF", "포지션_C",
            "선발1", "선발1_2", "선발3_4", "선발3_4_5", "선발4_5", "중계3_4_5"
    );

    public MethodologyService(ScoreDataLoader scoreDataLoader) {
        this.scoreDataLoader = scoreDataLoader;
    }

    public MethodologyResponse getMethodology() {
        // Formula Information
        MethodologyResponse.FormulaInfo formula = MethodologyResponse.FormulaInfo.builder()
                .perSkillFormula(new MethodologyResponse.FormulaItem(
                        "skillScore = Σ_effects ( weight × value × conditionProbability )",
                        "formula_per_skill"))
                .totalFormula(new MethodologyResponse.FormulaItem(
                        "total = Σ skillScore",
                        "formula_total"))
                .percentEffectRule(new MethodologyResponse.FormulaItem(
                        "value = floor(baseValue × rawValue) (baseValue default: Normal stat = 120, Deck stat = 500)",
                        "formula_percent_effect"))
                .roundingRule(new MethodologyResponse.FormulaItem(
                        "round(x) = Math.round(x * 100) / 100 (rounded to 2 decimal places)",
                        "formula_rounding"))
                .conditionCombinationRule(new MethodologyResponse.FormulaItem(
                        "Condition tokens joined by '+': '모드_*' is max, '포지션_*'/'선발*'/'중계*' is max, others are multiplied",
                        "formula_condition_combination"))
                .build();

        // Stat Weights
        Map<String, Double> statWeights = scoreDataLoader.getStatWeights();

        // Static Probabilities
        List<MethodologyResponse.ConditionProbabilityEntry> staticProbabilities = new ArrayList<>();
        addStaticGroup(staticProbabilities, ScoreCalculator.getStaticConditionProbabilities());
        addStaticGroup(staticProbabilities, ScoreCalculator.getPlateSituationProbabilities());
        addStaticGroup(staticProbabilities, ScoreCalculator.getGameStateProbabilities());
        addStaticGroup(staticProbabilities, ScoreCalculator.getModeProbabilities());
        addStaticGroup(staticProbabilities, ScoreCalculator.getLaunchAngleProbabilities());

        // Batting Order Default Probabilities
        List<MethodologyResponse.ConditionProbabilityEntry> battingOrderProbabilities = ScoreCalculator.getBattingOrderDefaultProbabilities()
                .entrySet().stream()
                .map(e -> new MethodologyResponse.ConditionProbabilityEntry(
                        e.getKey(),
                        e.getValue(),
                        CONDITION_DESCRIPTION_KEYS.getOrDefault(e.getKey(), "")
                ))
                .collect(Collectors.toList());

        // Role Probabilities
        List<MethodologyResponse.RoleProbabilityEntry> roleProbabilities = new ArrayList<>();
        Map<String, double[]> inningWeightsMap = ScoreCalculator.getInningWeightsByRole();
        Map<String, Double> gutsMap = ScoreCalculator.getGutsProbabilitiesByRole();
        Map<String, Double> patienceBelowVelocityMap = ScoreCalculator.getPatienceBelowVelocityProbabilitiesByRole();
        Map<String, Double> durationMap = ScoreCalculator.getNineBatterDurationProbabilitiesByRole();
        Map<String, Double> maestroMap = ScoreCalculator.getMaestroCumulativeProbabilitiesByRole();

        List<String> roles = List.of("SP", "RP", "CP", "BATTER");
        for (String role : roles) {
            double[] arr = inningWeightsMap.getOrDefault(role, new double[0]);
            List<Double> inningWeights = Arrays.stream(arr).boxed().toList();
            double guts = gutsMap.getOrDefault(role, 0.0);
            double patienceBelowVelocity = patienceBelowVelocityMap.getOrDefault(role, 0.0);
            double duration = durationMap.getOrDefault(role, 0.0);
            double maestro = maestroMap.getOrDefault(role, 0.0);

            roleProbabilities.add(new MethodologyResponse.RoleProbabilityEntry(
                    role,
                    inningWeights,
                    guts,
                    patienceBelowVelocity,
                    duration,
                    maestro
            ));
        }

        // Reaches
        List<MethodologyResponse.ReachProbabilityEntry> reachProbabilities = List.of(
                new MethodologyResponse.ReachProbabilityEntry(
                        "TOP_ORDER",
                        "condition_top_order_reach",
                        Arrays.stream(ScoreCalculator.getTopOrderPlateAppearanceReach()).boxed().toList()
                ),
                new MethodologyResponse.ReachProbabilityEntry(
                        "MIDDLE_ORDER",
                        "condition_middle_order_reach",
                        Arrays.stream(ScoreCalculator.getMiddleOrderPlateAppearanceReach()).boxed().toList()
                ),
                new MethodologyResponse.ReachProbabilityEntry(
                        "LOWER_ORDER",
                        "condition_lower_order_reach",
                        Arrays.stream(ScoreCalculator.getLowerOrderPlateAppearanceReach()).boxed().toList()
                )
        );

        // Gates
        List<MethodologyResponse.GateEntry> gates = GATE_TOKENS.stream()
                .map(token -> new MethodologyResponse.GateEntry(
                        token,
                        CONDITION_DESCRIPTION_KEYS.getOrDefault(token, "")
                ))
                .collect(Collectors.toList());

        MethodologyResponse.ConditionProbabilitiesInfo conditionProbabilities = MethodologyResponse.ConditionProbabilitiesInfo.builder()
                .staticProbabilities(staticProbabilities)
                .roleProbabilities(roleProbabilities)
                .battingOrderProbabilities(battingOrderProbabilities)
                .reachProbabilities(reachProbabilities)
                .gates(gates)
                .build();

        return MethodologyResponse.builder()
                .formula(formula)
                .statWeights(statWeights)
                .conditionProbabilities(conditionProbabilities)
                .build();
    }

    private void addStaticGroup(
            List<MethodologyResponse.ConditionProbabilityEntry> target,
            Map<String, Double> source
    ) {
        source.forEach((k, v) -> target.add(new MethodologyResponse.ConditionProbabilityEntry(
                k,
                v,
                CONDITION_DESCRIPTION_KEYS.getOrDefault(k, "")
        )));
    }
}
