package com.rivals.skillsim.ui.calculator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.i18n.AppStrings
import com.rivals.skillsim.i18n.LanguageCode
import com.rivals.skillsim.ui.common.LabeledDropdown
import com.rivals.skillsim.ui.common.PrimaryActionButton
import com.rivals.skillsim.ui.common.ScoreHero
import com.rivals.skillsim.ui.common.SectionCard
import com.rivals.skillsim.ui.common.cardTypeLabel
import com.rivals.skillsim.ui.theme.RivalsOpponentStat

@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    languageCode: LanguageCode,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val subPositions = if (state.position == "PITCHER") {
        listOf("ALL", "SP", "RP", "CP")
    } else {
        listOf("ALL", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = AppStrings.t(languageCode, "calculator_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            SectionCard(title = AppStrings.t(languageCode, "score_settings")) {
                LabeledDropdown(
                    label = AppStrings.t(languageCode, "label_card_type"),
                    selected = state.cardType,
                    options = CardType.entries,
                    optionLabel = { cardTypeLabel(it.name) },
                    onSelected = viewModel::setCardType,
                )
                LabeledDropdown(
                    label = AppStrings.t(languageCode, "label_position"),
                    selected = state.position,
                    options = listOf("PITCHER", "BATTER"),
                    optionLabel = { it },
                    onSelected = viewModel::setPosition,
                )
                LabeledDropdown(
                    label = AppStrings.t(languageCode, "label_sub_position"),
                    selected = state.subPosition,
                    options = subPositions,
                    optionLabel = { if (it == "ALL") AppStrings.t(languageCode, "option_all_sub_positions") else it },
                    onSelected = viewModel::setSubPosition,
                )
                if (state.position == "BATTER") {
                    val battingOrderOptions = listOf(null) + (1..9).toList()
                    LabeledDropdown(
                        label = AppStrings.t(languageCode, "label_batting_order"),
                        selected = state.battingOrder,
                        options = battingOrderOptions,
                        optionLabel = { order ->
                            order?.toString() ?: AppStrings.t(languageCode, "option_average_batting_order")
                        },
                        onSelected = viewModel::updateBattingOrder,
                    )
                }
            }
        }

        item {
            Text(
                text = AppStrings.t(languageCode, "score_skill_slots"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        items(List(state.slotCount) { index -> index }) { index ->
            val selection = state.selections[index]
            val selectedSkill = state.skills.firstOrNull { it.skillId == selection.skillId }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${AppStrings.t(languageCode, "slot_label")} ${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    LabeledDropdown(
                        label = AppStrings.t(languageCode, "score_select_skill"),
                        selected = selection.skillId,
                        options = listOf("") + state.skills.map { it.skillId },
                        optionLabel = { skillId ->
                            state.skills.firstOrNull { it.skillId == skillId }?.name
                                ?: AppStrings.t(languageCode, "score_select_skill")
                        },
                        onSelected = { viewModel.updateSkill(index, it) },
                    )
                    val levelOptions = (1..(selectedSkill?.maxLevel ?: 1)).toList()
                    LabeledDropdown(
                        label = AppStrings.t(languageCode, "score_level"),
                        selected = selection.level.coerceIn(levelOptions.first(), levelOptions.last()),
                        options = levelOptions,
                        optionLabel = { level ->
                            selectedSkill?.levelLabels?.getOrNull(level - 1) ?: "Lv $level"
                        },
                        onSelected = { viewModel.updateLevel(index, it) },
                    )
                    TextButton(onClick = { viewModel.clearSlot(index) }) {
                        Text(AppStrings.t(languageCode, "score_clear_slot"))
                    }
                }
            }
        }

        item {
            SectionCard(title = AppStrings.t(languageCode, "score_user_stats")) {
                state.visibleStats.forEach { stat ->
                    OutlinedTextField(
                        value = "%.0f".format(state.userStats[stat] ?: 0.0),
                        onValueChange = { raw ->
                            viewModel.updateUserStat(stat, raw.toDoubleOrNull() ?: 0.0)
                        },
                        label = { Text(AppStrings.t(languageCode, "stat_$stat")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = viewModel::resetUserStats) {
                    Text(AppStrings.t(languageCode, "score_reset_stats"))
                }
            }
        }

        item {
            state.error?.let {
                Text(
                    text = AppStrings.t(languageCode, it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            PrimaryActionButton(
                text = if (state.calculating) AppStrings.t(languageCode, "loading") else AppStrings.t(languageCode, "score_calculate"),
                onClick = viewModel::calculate,
                enabled = !state.calculating,
                loading = state.calculating,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.result?.let { result ->
            item {
                SectionCard(title = AppStrings.t(languageCode, "score_result_ready")) {
                    ScoreHero(
                        label = AppStrings.t(languageCode, "score_total"),
                        value = result.total,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (result.perSkill.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.t(languageCode, "score_by_skill"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        result.perSkill.forEach { skill ->
                            Text(
                                text = "${skill.name}: ${"%.2f".format(skill.score)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    if (result.perStat.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AppStrings.t(languageCode, "score_by_stat"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        val opponentStats = if (state.position == "PITCHER") {
                            setOf("파워", "정확", "선구", "인내", "주루")
                        } else {
                            setOf("구속", "변화", "구위", "제구", "지구력")
                        }
                        val perStatMine = result.perStat.filter { it.stat !in opponentStats }
                        val perStatOpponent = result.perStat.filter { it.stat in opponentStats }

                        if (perStatMine.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AppStrings.t(languageCode, "score_stat_mine"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            perStatMine.forEach { stat ->
                                Text(
                                    text = "${AppStrings.t(languageCode, "stat_${stat.stat}")}: +${"%.2f".format(stat.value)}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (perStatOpponent.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = AppStrings.t(languageCode, "score_stat_opponent"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            perStatOpponent.forEach { stat ->
                                Text(
                                    text = "${AppStrings.t(languageCode, "stat_${stat.stat}")}: -${"%.2f".format(stat.value)}",
                                    color = RivalsOpponentStat,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
