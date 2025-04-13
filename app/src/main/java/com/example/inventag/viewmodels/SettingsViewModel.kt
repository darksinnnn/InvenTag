package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _esp32IpAddress = MutableStateFlow("")
    val esp32IpAddress: StateFlow<String> = _esp32IpAddress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            settingsRepository.getEsp32IpAddress().collect { ipAddress ->
                _esp32IpAddress.value = ipAddress
                _isLoading.value = false
            }
        }
    }

    fun saveEsp32IpAddress(ipAddress: String) {
        viewModelScope.launch {
            _isLoading.value = true
            settingsRepository.saveEsp32IpAddress(ipAddress)
            _isLoading.value = false
            _isSaved.value = true
        }
    }

    fun resetSavedState() {
        _isSaved.value = false
    }
}