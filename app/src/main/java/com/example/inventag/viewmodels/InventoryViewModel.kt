package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.InventoryRepository
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.models.InventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class InventoryFilter(
    val category: String? = null,
    val showExpired: Boolean = true,
    val showLowStock: Boolean = true
)

// New data class to replace Triple
data class InventoryItemData(
    val name: String,
    val quantity: Int,
    val expiryDate: Date?,
    val category: String?
)

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

    // NFC tag scanning properties
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _detectedTagId = MutableStateFlow<String?>(null)
    val detectedTagId: StateFlow<String?> = _detectedTagId.asStateFlow()

    private val _pendingItemId = MutableStateFlow<String?>(null)
    val pendingItemId: StateFlow<String?> = _pendingItemId.asStateFlow()

    private val _pendingItemData = MutableStateFlow<InventoryItemData?>(null)
    val pendingItemData: StateFlow<InventoryItemData?> = _pendingItemData.asStateFlow()

    init {
        loadInventoryItems()
        loadCategories()

        // Observe NFC scans
        viewModelScope.launch {
            scannerRepository.nfcScans.collect { tagId ->
                if (tagId != null && _isScanning.value) {
                    _detectedTagId.value = tagId
                    _isScanning.value = false
                }
            }
        }
    }

    // Method to start NFC scanning for a new item
    fun startNfcScanForNewItem() {
        _isScanning.value = true
        viewModelScope.launch {
            scannerRepository.startNfcScan()
        }
    }

    // Method to start NFC scanning for an existing item
    fun startNfcScanForExistingItem(itemId: String) {
        _pendingItemId.value = itemId
        _isScanning.value = true
        viewModelScope.launch {
            scannerRepository.startNfcScan()
        }
    }

    // Method to stop NFC scanning
    fun stopNfcScan() {
        _isScanning.value = false
        viewModelScope.launch {
            scannerRepository.stopNfcScan()
        }
    }

    // Method to handle detected NFC tag
    fun onNfcTagDetected(tagId: String) {
        _detectedTagId.value = tagId
        _isScanning.value = false
    }

    // Method to clear detected tag
    fun clearDetectedTag() {
        _detectedTagId.value = null
        _pendingItemId.value = null
    }

    // Method to associate tag with pending item
    fun associateTagWithPendingItem() {
        viewModelScope.launch {
            try {
                val tagId = _detectedTagId.value ?: return@launch

                // If we have a pending item ID, associate the tag with it
                _pendingItemId.value?.let { itemId ->
                    inventoryRepository.associateTagWithItem(itemId, tagId)
                    _pendingItemId.value = null
                }

                // If we have pending item data, create a new item and associate the tag
                _pendingItemData.value?.let { itemData ->
                    val itemId = inventoryRepository.addInventoryItemAndGetId(
                        itemData.name,
                        itemData.quantity,
                        itemData.expiryDate, // This can be null now
                        itemData.category ?: "Uncategorized"
                    )
                    inventoryRepository.associateTagWithItem(itemId, tagId)
                    _pendingItemData.value = null
                }

                _detectedTagId.value = null
            } catch (e: Exception) {
                // Handle error
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

    fun addItem(name: String, quantity: Int, expiryDate: Date?, category: String) {
        viewModelScope.launch {
            inventoryRepository.addInventoryItem(name, quantity, expiryDate, category)
        }
    }

    fun updateItem(id: String, name: String, quantity: Int, expiryDate: Date?, category: String) {
        viewModelScope.launch {
            inventoryRepository.updateInventoryItem(id, name, quantity, expiryDate, category)
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
