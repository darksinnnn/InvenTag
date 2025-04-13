package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.InventoryRepository
import com.example.inventag.data.repositories.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class InventorySummary(
    val totalItems: Int = 0,
    val categories: Int = 0,
    val lowStockItems: Int = 0,
    val expiredItems: Int = 0
)

data class ScanSummary(
    val totalScans: Int = 0,
    val todayScans: Int = 0,
    val topItems: List<Pair<String, Int>> = emptyList()
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _inventorySummary = MutableStateFlow(InventorySummary())
    val inventorySummary: StateFlow<InventorySummary> = _inventorySummary.asStateFlow()

    private val _scanSummary = MutableStateFlow(ScanSummary())
    val scanSummary: StateFlow<ScanSummary> = _scanSummary.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadReports()
    }

    private fun loadReports() {
        viewModelScope.launch {
            _isLoading.value = true

            // Load inventory summary
            inventoryRepository.getInventoryItems().collect { items ->
                val categories = items.map { it.category }.distinct().size
                val lowStockItems = items.count { it.isLowStock() }
                val expiredItems = items.count { it.isExpired() }

                _inventorySummary.value = InventorySummary(
                    totalItems = items.size,
                    categories = categories,
                    lowStockItems = lowStockItems,
                    expiredItems = expiredItems
                )

                _isLoading.value = false
            }

            // Load scan summary
            scanRepository.getScanRecords().collect { records ->
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val todayStart = calendar.time

                val todayScans = records.count { it.scanDate.toDate().after(todayStart) }

                // Calculate top scanned items
                val itemCounts = records
                    .groupBy { it.itemName }
                    .mapValues { it.value.size }
                    .toList()
                    .sortedByDescending { it.second }
                    .take(5)

                _scanSummary.value = ScanSummary(
                    totalScans = records.size,
                    todayScans = todayScans,
                    topItems = itemCounts
                )
            }
        }
    }
}

