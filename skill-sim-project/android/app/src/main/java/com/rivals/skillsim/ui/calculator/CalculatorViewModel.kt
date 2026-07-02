package com.rivals.skillsim.ui.calculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivals.skillsim.data.local.CalculatorSettings
import com.rivals.skillsim.data.local.UserPrefsStore
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.data.model.ScoreRequest
import com.rivals.skillsim.data.model.ScoreResponse
import com.rivals.skillsim.data.model.ScoreSelection
import com.rivals.skillsim.data.model.ScoreSkillOption
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import com.rivals.skillsim.domain.AppRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DefaultUserStat = 120.0
private const val DefaultDeckScore = 500.0

private val BatterStats = listOf("파워", "정확", "선구", "인내", "주루", "수비")
private val PitcherStats = listOf("구속", "변화", "구위", "제구", "지구력", "수비")
private val DeckStats = listOf("스페셜덱", "팀덱")

data class ScoreSlotSelection(
    val skillId: String = "",
    val level: Int = 1,
)

data class CalculatorUiState(
    val cardType: CardType = CardType.SIGNATURE,
    val position: String = "BATTER",
    val subPosition: String = "ALL",
    val skills: List<ScoreSkillOption> = emptyList(),
    val selections: List<ScoreSlotSelection> = List(AppRules.slotCountFor(CardType.SIGNATURE)) { ScoreSlotSelection() },
    val result: ScoreResponse? = null,
    val loadingSkills: Boolean = false,
    val calculating: Boolean = false,
    val error: String? = null,
    val userStats: Map<String, Double> = defaultUserStats(),
    val battingOrder: Int? = null,
) {
    val slotCount: Int = AppRules.slotCountFor(cardType)
    val scorePosition: String = if (subPosition == "ALL") position else subPosition
    val visibleStats: List<String> = (if (position == "PITCHER") PitcherStats else BatterStats) + DeckStats
    val canCalculate: Boolean = selections.size == slotCount && selections.all { it.skillId.isNotBlank() }
}

class CalculatorViewModel(
    private val repository: SkillRepositoryContract,
    private val userPrefsStore: UserPrefsStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(CalculatorUiState())
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    init {
        if (userPrefsStore == null) {
            loadSkills()
        } else {
            restorePreferences()
        }
    }

    fun setCardType(cardType: CardType) {
        _state.update { current ->
            current.copy(
                cardType = cardType,
                selections = List(AppRules.slotCountFor(cardType)) { index ->
                    current.selections.getOrNull(index) ?: ScoreSlotSelection()
                },
                result = null,
            )
        }
        persistCalculatorSettings()
        loadSkills()
    }

    fun setPosition(position: String) {
        _state.update {
            it.copy(position = position, subPosition = "ALL", battingOrder = null, result = null)
        }
        persistCalculatorSettings()
        loadSkills()
    }

    fun setSubPosition(subPosition: String) {
        _state.update { it.copy(subPosition = subPosition, result = null) }
        persistCalculatorSettings()
        loadSkills()
    }

    fun updateSkill(slotIndex: Int, skillId: String) {
        _state.update { current ->
            if (slotIndex !in 0 until current.slotCount) return@update current
            current.copy(
                selections = current.selections.mapIndexed { index, selection ->
                    if (index != slotIndex) return@mapIndexed selection
                    val selectedSkill = current.skills.firstOrNull { it.skillId == skillId }
                    val nextLevel = selectedSkill?.let { selection.level.coerceIn(1, it.maxLevel.coerceAtLeast(1)) } ?: 1
                    ScoreSlotSelection(skillId = skillId, level = nextLevel)
                },
                result = null,
            )
        }
    }

    fun updateLevel(slotIndex: Int, level: Int) {
        _state.update { current ->
            if (slotIndex !in 0 until current.slotCount) return@update current
            current.copy(
                selections = current.selections.mapIndexed { index, selection ->
                    if (index == slotIndex) selection.copy(level = level.coerceAtLeast(1)) else selection
                },
                result = null,
            )
        }
    }

    fun clearSlot(slotIndex: Int) {
        _state.update { current ->
            if (slotIndex !in 0 until current.slotCount) return@update current
            current.copy(
                selections = current.selections.mapIndexed { index, selection ->
                    if (index == slotIndex) ScoreSlotSelection() else selection
                },
                result = null,
            )
        }
    }

    fun updateUserStat(stat: String, value: Double) {
        val nextValue = if (value.isFinite()) value.coerceAtLeast(0.0) else defaultValueForStat(stat)
        _state.update { current ->
            current.copy(userStats = current.userStats + (stat to nextValue), result = null)
        }
        persistUserStats()
    }

    fun updateBattingOrder(value: Int?) {
        _state.update {
            it.copy(battingOrder = value?.takeIf { order -> order in 1..9 }, result = null)
        }
        persistCalculatorSettings()
    }

    fun resetUserStats() {
        _state.update { it.copy(userStats = defaultUserStats(), result = null) }
        persistUserStats()
    }

    fun calculate() {
        val snapshot = state.value
        if (!snapshot.canCalculate) {
            _state.update { it.copy(error = "score_fill_slots") }
            return
        }
        val payload = ScoreRequest(
            cardType = snapshot.cardType.name,
            position = snapshot.scorePosition,
            selections = snapshot.selections.map { selection ->
                ScoreSelection(skillId = selection.skillId, level = selection.level)
            },
            battingOrder = snapshot.battingOrder.takeIf { snapshot.position == "BATTER" },
            userStats = snapshot.userStats,
        )

        viewModelScope.launch {
            _state.update { it.copy(calculating = true, error = null) }
            runCatching { repository.calculateScore(payload) }
                .onSuccess { response ->
                    _state.update { it.copy(calculating = false, result = response) }
                }
                .onFailure {
                    _state.update { it.copy(calculating = false, result = null, error = "score_error_calculate") }
                }
        }
    }

    private fun loadSkills() {
        val snapshot = state.value
        viewModelScope.launch {
            _state.update { it.copy(loadingSkills = true, error = null) }
            runCatching { repository.fetchScoreSkills(snapshot.cardType.name, snapshot.scorePosition) }
                .onSuccess { skills ->
                    _state.update { current ->
                        current.copy(
                            loadingSkills = false,
                            skills = skills,
                            selections = List(current.slotCount) { ScoreSlotSelection() },
                            result = null,
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(loadingSkills = false, skills = emptyList(), result = null, error = "score_error_load")
                    }
                }
        }
    }

    private fun restorePreferences() {
        val prefsStore = userPrefsStore ?: return
        viewModelScope.launch {
            val snapshot = prefsStore.preferences.first()
            _state.update { current ->
                current.copy(
                    cardType = snapshot.calculatorSettings.cardType,
                    position = snapshot.calculatorSettings.position,
                    subPosition = snapshot.calculatorSettings.subPosition,
                    selections = List(AppRules.slotCountFor(snapshot.calculatorSettings.cardType)) { ScoreSlotSelection() },
                    userStats = defaultUserStats() + snapshot.userStats,
                    battingOrder = snapshot.calculatorSettings.battingOrder,
                )
            }
            loadSkills()
        }
    }

    private fun persistCalculatorSettings() {
        val prefsStore = userPrefsStore ?: return
        val snapshot = state.value
        viewModelScope.launch {
            prefsStore.saveCalculatorSettings(
                CalculatorSettings(
                    cardType = snapshot.cardType,
                    position = snapshot.position,
                    subPosition = snapshot.subPosition,
                    battingOrder = snapshot.battingOrder,
                ),
            )
        }
    }

    private fun persistUserStats() {
        val prefsStore = userPrefsStore ?: return
        val userStats = state.value.userStats
        viewModelScope.launch {
            prefsStore.saveUserStats(userStats)
        }
    }
}

private fun defaultUserStats(): Map<String, Double> =
    (BatterStats + PitcherStats + DeckStats).associateWith(::defaultValueForStat)

private fun defaultValueForStat(stat: String): Double =
    if (stat in DeckStats) DefaultDeckScore else DefaultUserStat
