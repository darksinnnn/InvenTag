package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.AlertRepository
import com.example.inventag.models.Alert
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAlerts()
    }

    private fun loadAlerts() {
        viewModelScope.launch {
            _isLoading.value = true
            alertRepository.getAlerts().collect { alertList ->
                _alerts.value = alertList
                _isLoading.value = false
            }
        }
    }

    fun markAsRead(alertId: String) {
        viewModelScope.launch {
            alertRepository.markAlertAsRead(alertId)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            alertRepository.markAllAlertsAsRead()
        }
    }
}

