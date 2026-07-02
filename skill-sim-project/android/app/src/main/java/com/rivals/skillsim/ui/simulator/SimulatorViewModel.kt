package com.rivals.skillsim.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivals.skillsim.data.model.CardType
import com.rivals.skillsim.data.model.Grade
import com.rivals.skillsim.data.model.RollRequest
import com.rivals.skillsim.data.model.SkillSlot
import com.rivals.skillsim.data.model.TicketType
import com.rivals.skillsim.data.model.Tier
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import com.rivals.skillsim.domain.AppRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SimulatorUiState(
    val cardType: CardType = CardType.SIGNATURE,
    val ticketType: TicketType = TicketType.SKILL_CHANGE,
    val position: String = "PITCHER",
    val subPosition: String = "ALL",
    val useLevelProtectionSlots: List<Boolean> = List(AppRules.slotCountFor(CardType.SIGNATURE)) { false },
    val slots: List<SkillSlot> = emptyList(),
    val totalScore: Double = 0.0,
    val lockSlot1: Boolean = false,
    val availableThemes: List<String> = emptyList(),
    val selectedTheme: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val candidateSkills: List<SkillSlot>? = null,
    val candidateTotalScore: Double = 0.0,
    val ticketUsageCounts: Map<TicketType, Int> = TicketType.entries.associateWith { 0 },
    val protectionUsageCount: Int = 0,
) {
    val slotCount: Int = AppRules.slotCountFor(cardType)
    val normalizedSubPosition: String? = subPosition.takeUnless { it == "ALL" }
    val isSlot1Moment: Boolean = slots.firstOrNull()?.skill?.tier == Tier.MOMENT
    val canLockSlot1: Boolean = cardType == CardType.SIGNATURE || cardType == CardType.WBC || (cardType == CardType.MOMENT && isSlot1Moment)
}

class SimulatorViewModel(
    private val repository: SkillRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(SimulatorUiState())
    val state: StateFlow<SimulatorUiState> = _state.asStateFlow()

    fun setCardType(cardType: CardType) {
        val slotCount = AppRules.slotCountFor(cardType)
        _state.update {
            it.copy(
                cardType = cardType,
                useLevelProtectionSlots = List(slotCount) { false },
                slots = emptyList(),
                totalScore = 0.0,
                lockSlot1 = false,
                candidateSkills = null,
                candidateTotalScore = 0.0,
                error = null,
            )
        }
        loadThemes()
    }

    fun setTicketType(ticketType: TicketType) {
        _state.update { it.copy(ticketType = ticketType) }
    }

    fun setPosition(position: String) {
        _state.update { it.copy(position = position, subPosition = "ALL", error = null) }
        loadThemes()
    }

    fun setSubPosition(subPosition: String) {
        _state.update { it.copy(subPosition = subPosition, error = null) }
        loadThemes()
    }

    fun loadThemes() {
        val snapshot = state.value
        if (snapshot.cardType != CardType.MOMENT) {
            _state.update { it.copy(availableThemes = emptyList(), selectedTheme = null) }
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.fetchThemes(
                    position = snapshot.position.uppercase(),
                    subPosition = snapshot.normalizedSubPosition?.uppercase()
                )
            }.onSuccess { themes ->
                _state.update { current ->
                    val defaultTheme = themes.firstOrNull()
                    val nextSelected = current.selectedTheme?.takeIf { it in themes } ?: defaultTheme
                    current.copy(
                        availableThemes = themes,
                        selectedTheme = nextSelected,
                        error = null,
                    )
                }
            }.onFailure {
                _state.update { current ->
                    current.copy(
                        availableThemes = emptyList(),
                        selectedTheme = null,
                        error = "Moment themes could not be loaded."
                    )
                }
            }
        }
    }

    fun setSelectedTheme(theme: String?) {
        _state.update { it.copy(selectedTheme = theme) }
    }

    fun toggleLevelProtection(slotIndex: Int) {
        _state.update { current ->
            if (slotIndex !in 0 until current.slotCount) return@update current
            current.copy(
                useLevelProtectionSlots = List(current.slotCount) { index ->
                    if (index == slotIndex) !(current.useLevelProtectionSlots.getOrElse(index) { false })
                    else current.useLevelProtectionSlots.getOrElse(index) { false }
                },
            )
        }
    }

    fun toggleLockSlot1() {
        _state.update { current ->
            if (current.canLockSlot1) {
                current.copy(lockSlot1 = !current.lockSlot1)
            } else {
                current
            }
        }
    }

    fun roll() {
        val snapshot = state.value
        val protectionSlots = List(snapshot.slotCount) { index ->
            snapshot.useLevelProtectionSlots.getOrElse(index) { false }
        }
        val isLockActive = snapshot.lockSlot1 && snapshot.canLockSlot1
        val payload = RollRequest(
            cardType = snapshot.cardType,
            ticketType = snapshot.ticketType,
            useLevelProtectionSlots = protectionSlots,
            lockedSlots = if (isLockActive) listOf(0) else emptyList(),
            currentSkillIds = currentSkillIds(snapshot),
            currentGrades = currentGrades(snapshot),
            selectedTheme = snapshot.selectedTheme.takeIf { snapshot.cardType == CardType.MOMENT },
            position = snapshot.position,
            subPosition = snapshot.normalizedSubPosition,
        )

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.rollSkills(payload) }
                .onSuccess { response ->
                    val premiumFlow = snapshot.ticketType == TicketType.PREMIUM_SKILL_CHANGE ||
                        snapshot.ticketType == TicketType.SUPREME_SKILL_CHANGE
                    _state.update { current ->
                        val nextUsage = current.ticketUsageCounts.toMutableMap()
                        nextUsage[snapshot.ticketType] = (nextUsage[snapshot.ticketType] ?: 0) + 1
                        if (premiumFlow) {
                            current.copy(
                                loading = false,
                                candidateSkills = response.slots,
                                candidateTotalScore = response.totalScore,
                                ticketUsageCounts = nextUsage,
                                protectionUsageCount = current.protectionUsageCount + protectionSlots.count { it },
                            )
                        } else {
                            val nextSlots = response.slots
                            val isSlot1Moment = nextSlots.firstOrNull()?.skill?.tier == Tier.MOMENT
                            val nextCanLock = current.cardType == CardType.SIGNATURE || current.cardType == CardType.WBC || (current.cardType == CardType.MOMENT && isSlot1Moment)
                            val nextLock = current.lockSlot1 && nextCanLock
                            current.copy(
                                loading = false,
                                slots = nextSlots,
                                totalScore = response.totalScore,
                                candidateSkills = null,
                                candidateTotalScore = 0.0,
                                lockSlot1 = nextLock,
                                ticketUsageCounts = nextUsage,
                                protectionUsageCount = current.protectionUsageCount + protectionSlots.count { it },
                            )
                        }
                    }
                }
                .onFailure {
                    _state.update { current ->
                        current.copy(loading = false, error = "Failed to roll skills. Please try again.")
                    }
                }
        }
    }

    fun loadInitialSkills() {
        val snapshot = state.value
        viewModelScope.launch {
            runCatching {
                repository.fetchInitialSkills(
                    cardType = snapshot.cardType.name,
                    position = snapshot.position,
                    subPosition = snapshot.normalizedSubPosition,
                )
            }.onSuccess { response ->
                _state.update { current ->
                    val nextSlots = response.slots
                    val isSlot1Moment = nextSlots.firstOrNull()?.skill?.tier == Tier.MOMENT
                    val nextCanLock = current.cardType == CardType.SIGNATURE || current.cardType == CardType.WBC || (current.cardType == CardType.MOMENT && isSlot1Moment)
                    val nextLock = current.lockSlot1 && nextCanLock
                    current.copy(
                        slots = nextSlots,
                        totalScore = response.totalScore,
                        lockSlot1 = nextLock,
                        error = null,
                    )
                }
            }.onFailure {
                _state.update { current ->
                    current.copy(slots = emptyList(), totalScore = 0.0, lockSlot1 = false)
                }
            }
        }
    }

    fun keepCurrentSkills() {
        _state.update { it.copy(candidateSkills = null, candidateTotalScore = 0.0) }
    }

    fun applyCandidateSkills() {
        _state.update { current ->
            if (current.candidateSkills == null) {
                current
            } else {
                val nextSlots = current.candidateSkills
                val isSlot1Moment = nextSlots.firstOrNull()?.skill?.tier == Tier.MOMENT
                val nextCanLock = current.cardType == CardType.SIGNATURE || current.cardType == CardType.WBC || (current.cardType == CardType.MOMENT && isSlot1Moment)
                val nextLock = current.lockSlot1 && nextCanLock
                current.copy(
                    slots = nextSlots,
                    totalScore = current.candidateTotalScore,
                    candidateSkills = null,
                    candidateTotalScore = 0.0,
                    lockSlot1 = nextLock,
                )
            }
        }
    }

    fun resetUsageCounts() {
        _state.update {
            it.copy(
                ticketUsageCounts = TicketType.entries.associateWith { 0 },
                protectionUsageCount = 0,
            )
        }
    }

    private fun currentGrades(state: SimulatorUiState): List<Grade> =
        List(state.slotCount) { index -> state.slots.getOrNull(index)?.grade ?: Grade.D }

    private fun currentSkillIds(state: SimulatorUiState): List<Long?> =
        List(state.slotCount) { index -> state.slots.getOrNull(index)?.skill?.id }
}
