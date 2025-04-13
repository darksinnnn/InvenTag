package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.ScanRepository
import com.example.inventag.models.ScanRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _scanRecords = MutableStateFlow<List<ScanRecord>>(emptyList())
    val scanRecords: StateFlow<List<ScanRecord>> = _scanRecords.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadScanRecords()
    }

    private fun loadScanRecords() {
        viewModelScope.launch {
            _isLoading.value = true
            scanRepository.getScanRecords().collect { records ->
                _scanRecords.value = records
                _isLoading.value = false
            }
        }
    }

    fun applyFilters(startDate: Date?, endDate: Date?, isValid: Boolean?) {
        viewModelScope.launch {
            _isLoading.value = true
            scanRepository.getFilteredScanRecords(startDate, endDate, isValid).collect { records ->
                _scanRecords.value = records
                _isLoading.value = false
            }
        }
    }
}

