package com.example.skillsim.dto

data class MethodologyResponse(
    val formula: FormulaInfo,
    val statWeights: Map<String, Double>,
    val conditionProbabilities: ConditionProbabilitiesInfo,
) {
    data class FormulaInfo(
        val perSkillFormula: FormulaItem,
        val totalFormula: FormulaItem,
        val percentEffectRule: FormulaItem,
        val roundingRule: FormulaItem,
        val conditionCombinationRule: FormulaItem,
    )

    data class FormulaItem(
        val displayText: String,
        val descriptionKey: String,
    )

    data class ConditionProbabilitiesInfo(
        val staticProbabilities: List<ConditionProbabilityEntry>,
        val roleProbabilities: List<RoleProbabilityEntry>,
        val battingOrderProbabilities: List<ConditionProbabilityEntry>,
        val reachProbabilities: List<ReachProbabilityEntry>,
        val gates: List<GateEntry>,
    )

    data class ConditionProbabilityEntry(
        val token: String,
        val value: Double,
        val descriptionKey: String,
    )

    data class RoleProbabilityEntry(
        val role: String,
        val inningWeights: List<Double>,
        val gutsProbability: Double,
        val patienceBelowVelocityProbability: Double,
        val nineBatterDuration: Double,
        val maestroCumulative: Double,
    )

    data class ReachProbabilityEntry(
        val orderGroup: String,
        val descriptionKey: String,
        val reachProbabilities: List<Double>,
    )

    data class GateEntry(
        val token: String,
        val descriptionKey: String,
    )
}
