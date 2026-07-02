package com.rivals.skillsim.ui.simulator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.data.model.TicketType
import com.rivals.skillsim.i18n.AppStrings
import com.rivals.skillsim.i18n.LanguageCode
import com.rivals.skillsim.ui.common.GradeChip
import com.rivals.skillsim.ui.common.InfoChip
import com.rivals.skillsim.ui.common.LabeledDropdown
import com.rivals.skillsim.ui.common.PrimaryActionButton
import com.rivals.skillsim.ui.common.ScoreHero
import com.rivals.skillsim.ui.common.SectionCard
import com.rivals.skillsim.ui.common.cardTypeLabel

@Composable
fun SimulatorScreen(
    viewModel: SimulatorViewModel,
    languageCode: LanguageCode,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.cardType, state.position, state.subPosition) {
        viewModel.loadInitialSkills()
    }
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
                text = AppStrings.t(languageCode, "hdr_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            SectionCard(title = AppStrings.t(languageCode, "section_roll_settings")) {
                LabeledDropdown(
                    label = AppStrings.t(languageCode, "label_card_type"),
                    selected = state.cardType,
                    options = CardType.entries,
                    optionLabel = { cardTypeLabel(it.name) },
                    onSelected = viewModel::setCardType,
                )
                LabeledDropdown(
                    label = AppStrings.t(languageCode, "label_ticket_type"),
                    selected = state.ticketType,
                    options = TicketType.entries,
                    optionLabel = { ticketLabel(languageCode, it) },
                    onSelected = viewModel::setTicketType,
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
                if (state.cardType == CardType.MOMENT) {
                    LabeledDropdown(
                        label = AppStrings.t(languageCode, "label_theme"),
                        selected = state.selectedTheme ?: "",
                        options = state.availableThemes,
                        optionLabel = { it },
                        onSelected = viewModel::setSelectedTheme,
                    )
                    if (state.availableThemes.isEmpty()) {
                        Text(
                            text = AppStrings.t(languageCode, "no_theme"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.ticketUsageCounts.forEach { (ticketType, count) ->
                    InfoChip(text = "${ticketLabel(languageCode, ticketType)}: $count")
                }
                InfoChip(text = "${AppStrings.t(languageCode, "protection_used")}: ${state.protectionUsageCount}")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreHero(
                    label = AppStrings.t(languageCode, "score_total"),
                    value = state.totalScore,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        itemsIndexed(List(state.slotCount) { index -> state.slots.getOrNull(index) }) { index, slot ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (index == 0 && state.lockSlot1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${AppStrings.t(languageCode, "slot_label")} ${index + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (index == 0) {
                            IconButton(
                                onClick = viewModel::toggleLockSlot1,
                                enabled = state.canLockSlot1,
                            ) {
                                Icon(
                                    imageVector = if (state.lockSlot1) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                                    contentDescription = if (state.lockSlot1) {
                                        AppStrings.t(languageCode, "locked")
                                    } else {
                                        AppStrings.t(languageCode, "lock_slot1")
                                    },
                                    tint = if (state.lockSlot1) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        } else {
                            Text(
                                text = AppStrings.t(languageCode, "unlocked"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Text(
                        text = slot?.skill?.name ?: AppStrings.t(languageCode, "no_skill"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    slot?.let {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GradeChip(grade = it.grade.name)
                            InfoChip(text = it.skill?.tier?.name ?: "")
                            InfoChip(text = it.score?.let { score -> "%.2f".format(score) } ?: "-")
                        }
                    }
                    if (state.cardType != CardType.MOMENT) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Switch(
                                checked = state.useLevelProtectionSlots.getOrElse(index) { false },
                                onCheckedChange = { viewModel.toggleLevelProtection(index) },
                            )
                            Text(
                                text = AppStrings.t(languageCode, "protect_label"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        state.candidateSkills?.let { candidates ->
            item {
                SectionCard(title = AppStrings.t(languageCode, "new_skills")) {
                    candidates.forEachIndexed { index, slot ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}. ${slot.skill?.name ?: AppStrings.t(languageCode, "no_skill")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            GradeChip(grade = slot.grade.name)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::keepCurrentSkills,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(AppStrings.t(languageCode, "current_skills"))
                        }
                        Button(
                            onClick = viewModel::applyCandidateSkills,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(AppStrings.t(languageCode, "confirm_action"))
                        }
                    }
                }
            }
        }

        item {
            PrimaryActionButton(
                text = if (state.loading) AppStrings.t(languageCode, "loading") else AppStrings.t(languageCode, "btn_roll"),
                onClick = viewModel::roll,
                enabled = !state.loading,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun ticketLabel(languageCode: LanguageCode, ticketType: TicketType): String =
    when (ticketType) {
        TicketType.SKILL_CHANGE -> AppStrings.t(languageCode, "ticket_skill_change")
        TicketType.PREMIUM_SKILL_CHANGE -> AppStrings.t(languageCode, "ticket_premium")
        TicketType.SUPREME_SKILL_CHANGE -> AppStrings.t(languageCode, "ticket_supreme")
    }
