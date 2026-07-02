package com.rivals.skillsim.ui.warmup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rivals.skillsim.data.repository.SkillRepositoryContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class WarmupState {
    Checking, Waking, Ready, Error
}

class WarmupViewModel(
    private val repository: SkillRepositoryContract
) : ViewModel() {

    private val _state = MutableStateFlow(WarmupState.Checking)
    val state: StateFlow<WarmupState> = _state.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private var pollJob: Job? = null
    private var timerJob: Job? = null
    private var startTime = 0L

    init {
        startWarmup()
    }

    fun startWarmup() {
        pollJob?.cancel()
        timerJob?.cancel()

        _state.value = WarmupState.Checking
        _elapsedSeconds.value = 0
        startTime = System.currentTimeMillis()

        // Timer job to update elapsed time, handle soft-cap (90s) and overlay delay (1200ms)
        timerJob = viewModelScope.launch {
            while (_state.value != WarmupState.Ready && _state.value != WarmupState.Error) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                _elapsedSeconds.value = (elapsed / 1000).toInt()

                if (elapsed >= 90000) {
                    _state.value = WarmupState.Error
                    cancelJobs()
                    break
                }

                if (_state.value == WarmupState.Checking && elapsed >= 1200) {
                    _state.value = WarmupState.Waking
                }
            }
        }

        // Polling job
        pollJob = viewModelScope.launch {
            while (_state.value != WarmupState.Ready && _state.value != WarmupState.Error) {
                val success = try {
                    val response = withTimeoutOrNull(8000) {
                        repository.checkHealth()
                    }
                    response?.status == "ok"
                } catch (e: Exception) {
                    false
                }

                if (success) {
                    _state.value = WarmupState.Ready
                    cancelJobs()
                    break
                } else {
                    delay(2000)
                }
            }
        }
    }

    private fun cancelJobs() {
        pollJob?.cancel()
        timerJob?.cancel()
    }

    fun retry() {
        startWarmup()
    }

    override fun onCleared() {
        super.onCleared()
        cancelJobs()
    }
}
