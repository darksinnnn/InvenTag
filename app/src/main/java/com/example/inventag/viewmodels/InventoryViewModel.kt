package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.InventoryRepository
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.models.InventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

// Sealed class for better state management of scan results
sealed class ScanResult {
    object Idle : ScanResult()
    object Loading : ScanResult()
    data class ExistingItem(val item: InventoryItem) : ScanResult()
    data class NewTag(val tagId: String, val unassignedItems: List<InventoryItem>) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

// Data classes remain the same
data class InventoryFilter(
    val category: String? = null,
    val showExpired: Boolean = true,
    val showLowStock: Boolean = true
)

data class InventoryItemData(
    val name: String,
    val quantity: Int,
    val expiryDate: Date?,
    val category: String?
)

// ## A new sealed class for one-time events from the ViewModel to the UI
sealed class AddItemEvent {
    data class Success(val itemId: String, val itemName: String) : AddItemEvent()
}


@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val scannerRepository: ScannerRepository
) : ViewModel() {

    private val _inventoryItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val inventoryItems: StateFlow<List<InventoryItem>> = _inventoryItems.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedItem = MutableStateFlow<InventoryItem?>(null)
    val selectedItem: StateFlow<InventoryItem?> = _selectedItem.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _currentFilter = MutableStateFlow(InventoryFilter())
    val currentFilter: StateFlow<InventoryFilter> = _currentFilter.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult>(ScanResult.Idle)
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()

    // ## A new SharedFlow to emit one-time events for the "Add Item" success dialog
    private val _addItemEvent = MutableSharedFlow<AddItemEvent>()
    val addItemEvent: SharedFlow<AddItemEvent> = _addItemEvent.asSharedFlow()


    init {
        loadInventoryItems()
        loadCategories()

        viewModelScope.launch {
            scannerRepository.nfcScans.collect { tagId ->
                if (tagId != null && _isScanning.value) {
                    _isScanning.value = false
                    processScannedTag(tagId)
                }
            }
        }
    }

    private fun processScannedTag(tagId: String) {
        viewModelScope.launch {
            _scanResult.value = ScanResult.Loading
            try {
                val existingItem = inventoryRepository.getItemByTagId(tagId)
                if (existingItem != null) {
                    _scanResult.value = ScanResult.ExistingItem(existingItem)
                } else {
                    val unassignedItems = inventoryRepository.getUnassignedItems()
                    _scanResult.value = ScanResult.NewTag(tagId, unassignedItems)
                }
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    fun startNfcScan() {
        _scanResult.value = ScanResult.Idle
        _isScanning.value = true
        viewModelScope.launch {
            scannerRepository.startNfcScan()
        }
    }

    fun stopNfcScan() {
        _isScanning.value = false
        viewModelScope.launch {
            scannerRepository.stopNfcScan()
        }
    }

    fun clearScanResult() {
        _scanResult.value = ScanResult.Idle
    }

    fun associateTagWithItem(itemId: String, tagId: String) {
        viewModelScope.launch {
            try {
                inventoryRepository.associateTagWithItem(itemId, tagId)
                clearScanResult()
            } catch (e: Exception) {
                _scanResult.value = ScanResult.Error("Failed to associate tag: ${e.message}")
            }
        }
    }

    private fun loadInventoryItems() {
        viewModelScope.launch {
            _isLoading.value = true
            inventoryRepository.getInventoryItems().collect { items ->
                _inventoryItems.value = applyFiltersToItems(items)
                _isLoading.value = false
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            inventoryRepository.getCategories().collect { categoryList ->
                _categories.value = categoryList
            }
        }
    }

    // ## This function now gets the new ID and emits a success event
    fun addItem(name: String, quantity: Int, expiryDate: Date?, category: String, price: Double?) {
        viewModelScope.launch {
            val newId = inventoryRepository.addInventoryItem(name, quantity, expiryDate, category, price)
            _addItemEvent.emit(AddItemEvent.Success(itemId = newId, itemName = name))
        }
    }

    fun updateItem(id: String, name: String, quantity: Int, expiryDate: Date?, category: String, price: Double?) {
        viewModelScope.launch {
            inventoryRepository.updateInventoryItem(id, name, quantity, expiryDate, category, price)
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            inventoryRepository.deleteInventoryItem(id)
        }
    }

    fun selectItemForEdit(item: InventoryItem) {
        _selectedItem.value = item
    }

    fun clearSelectedItem() {
        _selectedItem.value = null
    }

    fun applyFilters(category: String?, showExpired: Boolean, showLowStock: Boolean) {
        _currentFilter.value = InventoryFilter(category, showExpired, showLowStock)
        viewModelScope.launch {
            val allItems = inventoryRepository.getAllInventoryItems()
            _inventoryItems.value = applyFiltersToItems(allItems)
        }
    }

    private fun applyFiltersToItems(items: List<InventoryItem>): List<InventoryItem> {
        val filter = _currentFilter.value
        return items.filter { item ->
            val categoryMatch = filter.category == null || item.category == filter.category
            val expiredMatch = filter.showExpired || !item.isExpired()
            val lowStockMatch = filter.showLowStock || !item.isLowStock()

            categoryMatch && expiredMatch && lowStockMatch
        }
    }
}