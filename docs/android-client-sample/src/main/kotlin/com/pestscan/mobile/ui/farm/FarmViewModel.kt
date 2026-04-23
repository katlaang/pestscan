package com.pestscan.mobile.ui.farm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pestscan.mobile.data.repository.FarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FarmViewModel(
    private val repository: FarmRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FarmUiState())
    val state: StateFlow<FarmUiState> = _state.asStateFlow()

    init {
        observeFarms()
        refresh()
    }

    private fun observeFarms() {
        viewModelScope.launch {
            repository.farms.collect { farms ->
                _state.update { it.copy(farms = farms) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.refreshFarms() }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
            _state.update { it.copy(isLoading = false) }
        }
    }
}
