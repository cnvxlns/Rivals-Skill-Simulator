package com.rivals.skillsim.ui.methodology

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivals.skillsim.data.model.*
import com.rivals.skillsim.i18n.AppStrings
import com.rivals.skillsim.i18n.LanguageCode
import com.rivals.skillsim.ui.common.SectionCard

enum class MethodologyTab(val key: String) {
    Static("static"),
    Role("role"),
    Batting("batting"),
    Reach("reach"),
    Gates("gates")
}

@Composable
fun MethodologyScreen(
    viewModel: MethodologyViewModel,
    languageCode: LanguageCode,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val error = state.error
    if (error != null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = { viewModel.loadMethodology() }) {
                Text("Retry")
            }
        }
        return
    }

    val methodology = state.data
    if (methodology != null) {
        var activeTab by remember { mutableStateOf(MethodologyTab.Static) }

        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = AppStrings.t(languageCode, "tab_methodology").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = AppStrings.t(languageCode, "methodology_title"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.t(languageCode, "methodology_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Formulas Section
            item {
                Text(
                    text = AppStrings.t(languageCode, "methodology_section_formula"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                FormulaCard(
                    title = AppStrings.t(languageCode, methodology.formula.perSkillFormula.descriptionKey),
                    formulaText = methodology.formula.perSkillFormula.displayText
                )
            }

            item {
                FormulaCard(
                    title = AppStrings.t(languageCode, methodology.formula.totalFormula.descriptionKey),
                    formulaText = methodology.formula.totalFormula.displayText
                )
            }

            item {
                FormulaCard(
                    title = AppStrings.t(languageCode, methodology.formula.percentEffectRule.descriptionKey),
                    formulaText = methodology.formula.percentEffectRule.displayText,
                    description = AppStrings.t(languageCode, "formula_percent_effect")
                )
            }

            item {
                FormulaCard(
                    title = AppStrings.t(languageCode, methodology.formula.roundingRule.descriptionKey),
                    formulaText = methodology.formula.roundingRule.displayText
                )
            }

            item {
                FormulaCard(
                    title = AppStrings.t(languageCode, methodology.formula.conditionCombinationRule.descriptionKey),
                    formulaText = methodology.formula.conditionCombinationRule.displayText
                )
            }

            // Stat Weights Section
            item {
                Text(
                    text = AppStrings.t(languageCode, "methodology_section_stat_weights"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            item {
                SectionCard(title = AppStrings.t(languageCode, "methodology_section_stat_weights")) {
                    val statList = methodology.statWeights.toList()
                    statList.chunked(3).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { (stat, weight) ->
                                val localizedLabel = AppStrings.t(languageCode, "stat_$stat")
                                val displayLabel = if (localizedLabel != "stat_$stat") localizedLabel else stat
                                
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "%.2f".format(weight),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            // Add placeholders for empty cells in a non-full last row
                            if (chunk.size < 3) {
                                Spacer(modifier = Modifier.weight((3 - chunk.size).toFloat()))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Conditional Probabilities Section
            item {
                Text(
                    text = AppStrings.t(languageCode, "methodology_section_conditions"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            // Selector Tabs
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MethodologyTab.entries.forEach { tab ->
                        val labelKey = "methodology_group_${tab.key}"
                        val label = AppStrings.t(languageCode, labelKey)
                        val selected = tab == activeTab
                        
                        Surface(
                            modifier = Modifier
                                .clickable { activeTab = tab }
                                .clip(MaterialTheme.shapes.small),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tab Content rendering
            item {
                SectionCard(title = AppStrings.t(languageCode, "methodology_group_${activeTab.key}")) {
                    when (activeTab) {
                        MethodologyTab.Static -> {
                            ConditionProbabilityTable(
                                entries = methodology.conditionProbabilities.staticProbabilities,
                                languageCode = languageCode
                            )
                        }
                        MethodologyTab.Batting -> {
                            ConditionProbabilityTable(
                                entries = methodology.conditionProbabilities.battingOrderProbabilities,
                                languageCode = languageCode
                            )
                        }
                        MethodologyTab.Role -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                methodology.conditionProbabilities.roleProbabilities.forEach { entry ->
                                    RoleProbabilityCard(entry = entry, languageCode = languageCode)
                                }
                            }
                        }
                        MethodologyTab.Reach -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                methodology.conditionProbabilities.reachProbabilities.forEach { entry ->
                                    ReachProbabilityCard(entry = entry, languageCode = languageCode)
                                }
                            }
                        }
                        MethodologyTab.Gates -> {
                            GateTable(
                                entries = methodology.conditionProbabilities.gates,
                                languageCode = languageCode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormulaCard(
    title: String,
    formulaText: String,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                    .padding(8.dp)
            ) {
                Text(
                    text = formulaText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ConditionProbabilityTable(
    entries: List<ConditionProbabilityEntry>,
    languageCode: LanguageCode
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = AppStrings.t(languageCode, "methodology_col_token"),
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.t(languageCode, "methodology_col_value"),
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.t(languageCode, "methodology_col_description"),
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        
        // Table Rows
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.token,
                    modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "%.3f".format(entry.value),
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = translateToken(entry.token, entry.descriptionKey, languageCode),
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun GateTable(
    entries: List<GateEntry>,
    languageCode: LanguageCode
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = AppStrings.t(languageCode, "methodology_col_token"),
                modifier = Modifier.weight(1.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.t(languageCode, "methodology_col_value"),
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AppStrings.t(languageCode, "methodology_col_description"),
                modifier = Modifier.weight(2f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        
        // Table Rows
        entries.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.token,
                    modifier = Modifier.weight(1.5f),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "1.0 / 0.0",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = translateToken(entry.token, entry.descriptionKey, languageCode),
                    modifier = Modifier.weight(2f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun RoleProbabilityCard(
    entry: RoleProbabilityEntry,
    languageCode: LanguageCode
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Role: ${entry.role}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(8.dp)
                ) {
                    Text(
                        text = AppStrings.t(languageCode, "condition_guts_probability"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.2f".format(entry.gutsProbability),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(8.dp)
                ) {
                    Text(
                        text = AppStrings.t(languageCode, "condition_patience_below_velocity_probability"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.2f".format(entry.patienceBelowVelocityProbability),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(8.dp)
                ) {
                    Text(
                        text = AppStrings.t(languageCode, "condition_nine_batter_duration"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.2f".format(entry.nineBatterDuration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                        .padding(8.dp)
                ) {
                    Text(
                        text = AppStrings.t(languageCode, "condition_maestro_cumulative"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "%.3f".format(entry.maestroCumulative),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        val titleKey = "condition_${entry.role.lowercase()}_inning_weights"
        val defaultTitle = "${entry.role} Inning Weights (1~9)"
        val localizedTitle = AppStrings.t(languageCode, titleKey)
        val finalTitle = if (localizedTitle != titleKey) localizedTitle else defaultTitle
        
        Text(
            text = finalTitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            entry.inningWeights.forEachIndexed { idx, w ->
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${idx + 1}H",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.3f".format(w),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ReachProbabilityCard(
    entry: ReachProbabilityEntry,
    languageCode: LanguageCode
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val description = AppStrings.t(languageCode, entry.descriptionKey)
        Text(
            text = if (description != entry.descriptionKey) description else entry.orderGroup,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            entry.reachProbabilities.forEachIndexed { idx, p ->
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PA ${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%.3f".format(p),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun translateToken(token: String, descriptionKey: String, languageCode: LanguageCode): String {
    if (descriptionKey.isNotBlank()) {
        val valTranslated = AppStrings.t(languageCode, descriptionKey)
        if (valTranslated != descriptionKey) {
            return valTranslated
        }
    }
    return token
}
