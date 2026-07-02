package com.rivals.skillsim.data.model

import com.google.gson.annotations.SerializedName

data class FormulaItem(
    @SerializedName("displayText") val displayText: String,
    @SerializedName("descriptionKey") val descriptionKey: String
)

data class FormulaInfo(
    @SerializedName("perSkillFormula") val perSkillFormula: FormulaItem,
    @SerializedName("totalFormula") val totalFormula: FormulaItem,
    @SerializedName("percentEffectRule") val percentEffectRule: FormulaItem,
    @SerializedName("roundingRule") val roundingRule: FormulaItem,
    @SerializedName("conditionCombinationRule") val conditionCombinationRule: FormulaItem
)

data class ConditionProbabilityEntry(
    @SerializedName("token") val token: String,
    @SerializedName("value") val value: Double,
    @SerializedName("descriptionKey") val descriptionKey: String
)

data class RoleProbabilityEntry(
    @SerializedName("role") val role: String,
    @SerializedName("inningWeights") val inningWeights: List<Double>,
    @SerializedName("gutsProbability") val gutsProbability: Double,
    @SerializedName("nineBatterDuration") val nineBatterDuration: Double,
    @SerializedName("maestroCumulative") val maestroCumulative: Double
)

data class ReachProbabilityEntry(
    @SerializedName("orderGroup") val orderGroup: String,
    @SerializedName("descriptionKey") val descriptionKey: String,
    @SerializedName("reachProbabilities") val reachProbabilities: List<Double>
)

data class GateEntry(
    @SerializedName("token") val token: String,
    @SerializedName("descriptionKey") val descriptionKey: String
)

data class ConditionProbabilitiesInfo(
    @SerializedName("staticProbabilities") val staticProbabilities: List<ConditionProbabilityEntry>,
    @SerializedName("roleProbabilities") val roleProbabilities: List<RoleProbabilityEntry>,
    @SerializedName("battingOrderProbabilities") val battingOrderProbabilities: List<ConditionProbabilityEntry>,
    @SerializedName("reachProbabilities") val reachProbabilities: List<ReachProbabilityEntry>,
    @SerializedName("gates") val gates: List<GateEntry>
)

data class MethodologyResponse(
    @SerializedName("formula") val formula: FormulaInfo,
    @SerializedName("statWeights") val statWeights: Map<String, Double>,
    @SerializedName("conditionProbabilities") val conditionProbabilities: ConditionProbabilitiesInfo
)
