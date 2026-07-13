package com.pestscan.mobile.ui.farm

import com.pestscan.mobile.domain.model.Farm

data class FarmUiState(
    val isLoading: Boolean = false,
    val farms: List<Farm> = emptyList(),
    val error: String? = null
)
