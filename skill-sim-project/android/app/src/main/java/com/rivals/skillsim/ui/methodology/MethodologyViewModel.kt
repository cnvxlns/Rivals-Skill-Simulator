package com.rivals.skillsim.ui.methodology

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivals.skillsim.data.model.MethodologyResponse
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MethodologyUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val data: MethodologyResponse? = null,
)

class MethodologyViewModel(
    private val repository: SkillRepositoryContract,
) : ViewModel() {
    private val _state = MutableStateFlow(MethodologyUiState())
    val state: StateFlow<MethodologyUiState> = _state.asStateFlow()

    init {
        loadMethodology()
    }

    fun loadMethodology() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.fetchMethodology()
                _state.update { it.copy(loading = false, data = response) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.localizedMessage ?: "Failed to load methodology") }
            }
        }
    }
}
