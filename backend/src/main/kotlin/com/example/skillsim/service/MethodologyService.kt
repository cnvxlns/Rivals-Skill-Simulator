package com.example.skillsim.service

import com.example.skillsim.config.ScoreDataLoader
import com.example.skillsim.dto.MethodologyResponse
import org.springframework.stereotype.Service

/** 조건 토큰 → 화면에 띄울 설명의 i18n 키. */
private val CONDITION_DESCRIPTION_KEYS = mapOf(
    "ALWAYS" to "condition_always",
    "홈" to "condition_home",
    "원정" to "condition_away",
    "주자있음" to "condition_runners_on",
    "주자없음" to "condition_runners_off",
    "주자1명" to "condition_runner_1",
    "주자2명이상" to "condition_runners_2_or_more",
    "주자2루이상" to "condition_runner_2nd_or_higher",
    "주자3루" to "condition_runner_3rd",
    "OVR열세" to "condition_ovr_inferior",
    "OVR우세" to "condition_ovr_superior",
    "좌투상대" to "condition_vs_left_pitcher",
    "우투상대" to "condition_vs_right_pitcher",
    "좌타상대" to "condition_vs_left_batter",
    "우타상대" to "condition_vs_right_batter",
    "직구상대" to "condition_vs_fastball",
    "속구선택" to "condition_fastball_selected",
    "변화구상대" to "condition_vs_breakingball",
    "변화구선택" to "condition_breakingball_selected",
    "스윗스팟" to "condition_sweet_spot",
    "당겨치기" to "condition_pull",
    "밀어치기" to "condition_push",
    "높은공" to "condition_high_ball",
    "낮은공" to "condition_low_ball",
    "풀카운트" to "condition_full_count",
    "2아웃" to "condition_two_outs",
    "초구" to "condition_first_pitch",
    "스트라이크타격" to "condition_strike_hit",
    "1스트라이크" to "condition_one_strike",
    "2스트라이크" to "condition_two_strikes",
    "상대팀홈런3" to "condition_opponent_three_homeruns",
    "이닝출루2인이상" to "condition_inning_two_baserunners",
    "발사각조건" to "condition_launch_angle",
    "발사각10이상" to "condition_launch_angle_10_plus",
    "발사각14이하" to "condition_launch_angle_14_minus",
    "타순1" to "condition_batting_order_1",
    "타순1_2" to "condition_batting_order_1_2",
    "타순2_3" to "condition_batting_order_2_3",
    "타순3_4_5" to "condition_batting_order_3_5",
    "타순4_5" to "condition_batting_order_4_5",
    "타순6_9" to "condition_batting_order_6_9",
    "타순8_9" to "condition_batting_order_8_9",
    "리드" to "condition_lead",
    "리드아님" to "condition_not_leading",
    "비김또는리드" to "condition_tie_or_lead",
    "비김또는열세" to "condition_tie_or_behind",
    "모드_랭킹대전" to "condition_mode_ranking_match",
    "모드_랭킹토너먼트" to "condition_mode_ranking_tournament",
    "모드_라이브매치" to "condition_mode_live_match",
    "모드_리그" to "condition_mode_league",
    "모드_클럽" to "condition_mode_club",
    "모드_타점배틀" to "condition_mode_rbi_battle",
    "모드_랭킹슬러거" to "condition_mode_ranking_slugger",
    "포지션_SP" to "condition_gate_position_sp",
    "포지션_RP_CP" to "condition_gate_position_rp_cp",
    "포지션_DH" to "condition_gate_position_dh",
    "포지션_SS" to "condition_gate_position_ss",
    "포지션_OF" to "condition_gate_position_of",
    "포지션_C" to "condition_gate_position_c",
    "선발1" to "condition_gate_slot_sp_1",
    "선발1_2" to "condition_gate_slot_sp_1_2",
    "선발3_4" to "condition_gate_slot_sp_3_4",
    "선발3_4_5" to "condition_gate_slot_sp_3_5",
    "선발4_5" to "condition_gate_slot_sp_4_5",
    "중계3_4_5" to "condition_gate_slot_rp_3_5",
)

private val GATE_TOKENS = listOf(
    "포지션_SP", "포지션_RP_CP", "포지션_DH", "포지션_SS", "포지션_OF", "포지션_C",
    "선발1", "선발1_2", "선발3_4", "선발3_4_5", "선발4_5", "중계3_4_5",
)

private val ROLES = listOf("SP", "RP", "CP", "BATTER")

@Service
class MethodologyService(
    private val scoreDataLoader: ScoreDataLoader,
) {

    fun getMethodology(): MethodologyResponse {
        val formula = MethodologyResponse.FormulaInfo(
            perSkillFormula = MethodologyResponse.FormulaItem(
                "skillScore = Σ_effects ( weight × value × conditionProbability )",
                "formula_per_skill",
            ),
            totalFormula = MethodologyResponse.FormulaItem(
                "total = Σ skillScore",
                "formula_total",
            ),
            percentEffectRule = MethodologyResponse.FormulaItem(
                "value = floor(baseValue × rawValue) (baseValue default: Normal stat = 120, Deck stat = 500)",
                "formula_percent_effect",
            ),
            roundingRule = MethodologyResponse.FormulaItem(
                "round(x) = Math.round(x * 100) / 100 (rounded to 2 decimal places)",
                "formula_rounding",
            ),
            conditionCombinationRule = MethodologyResponse.FormulaItem(
                "Condition tokens joined by '+': '모드_*' is max, '포지션_*'/'선발*'/'중계*' is max, others are multiplied",
                "formula_condition_combination",
            ),
        )

        val staticProbabilities = listOf(
            ScoreCalculator.staticConditionProbabilities,
            ScoreCalculator.plateSituationProbabilities,
            ScoreCalculator.gameStateProbabilities,
            ScoreCalculator.modeProbabilities,
            ScoreCalculator.launchAngleProbabilities,
        ).flatMap { group -> group.map { (token, value) -> conditionEntry(token, value) } }

        val battingOrderProbabilities = ScoreCalculator.battingOrderDefaultProbabilities
            .map { (token, value) -> conditionEntry(token, value) }

        val inningWeightsByRole = ScoreCalculator.inningWeightsByRole
        val gutsByRole = ScoreCalculator.gutsProbabilitiesByRole
        val patienceBelowVelocityByRole = ScoreCalculator.patienceBelowVelocityProbabilitiesByRole
        val nineBatterDurationByRole = ScoreCalculator.nineBatterDurationProbabilitiesByRole
        val maestroByRole = ScoreCalculator.maestroCumulativeProbabilitiesByRole

        val roleProbabilities = ROLES.map { role ->
            MethodologyResponse.RoleProbabilityEntry(
                role = role,
                inningWeights = inningWeightsByRole[role]?.toList().orEmpty(),
                gutsProbability = gutsByRole[role] ?: 0.0,
                patienceBelowVelocityProbability = patienceBelowVelocityByRole[role] ?: 0.0,
                nineBatterDuration = nineBatterDurationByRole[role] ?: 0.0,
                maestroCumulative = maestroByRole[role] ?: 0.0,
            )
        }

        val reachProbabilities = listOf(
            MethodologyResponse.ReachProbabilityEntry(
                "TOP_ORDER",
                "condition_top_order_reach",
                ScoreCalculator.topOrderPlateAppearanceReach.toList(),
            ),
            MethodologyResponse.ReachProbabilityEntry(
                "MIDDLE_ORDER",
                "condition_middle_order_reach",
                ScoreCalculator.middleOrderPlateAppearanceReach.toList(),
            ),
            MethodologyResponse.ReachProbabilityEntry(
                "LOWER_ORDER",
                "condition_lower_order_reach",
                ScoreCalculator.lowerOrderPlateAppearanceReach.toList(),
            ),
        )

        val gates = GATE_TOKENS.map { token ->
            MethodologyResponse.GateEntry(token, CONDITION_DESCRIPTION_KEYS[token].orEmpty())
        }

        return MethodologyResponse(
            formula = formula,
            statWeights = scoreDataLoader.statWeights,
            conditionProbabilities = MethodologyResponse.ConditionProbabilitiesInfo(
                staticProbabilities = staticProbabilities,
                roleProbabilities = roleProbabilities,
                battingOrderProbabilities = battingOrderProbabilities,
                reachProbabilities = reachProbabilities,
                gates = gates,
            ),
        )
    }

    private fun conditionEntry(token: String, value: Double) =
        MethodologyResponse.ConditionProbabilityEntry(
            token = token,
            value = value,
            descriptionKey = CONDITION_DESCRIPTION_KEYS[token].orEmpty(),
        )
}
