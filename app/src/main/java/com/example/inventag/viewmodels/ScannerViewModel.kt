package com.example.inventag.viewmodels
import com.example.inventag.models.AlertType
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
import javax.inject.Inject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.inventag.data.repositories.AlertRepository
import android.util.Log


@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val inventoryRepository: InventoryRepository,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scannedItem = MutableStateFlow<InventoryItem?>(null)
    val scannedItem: StateFlow<InventoryItem?> = _scannedItem.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _tagId = MutableStateFlow<String?>(null)
    val tagId: StateFlow<String?> = _tagId.asStateFlow()

    private val _allInventoryItems = MutableStateFlow<List<InventoryItem>>(emptyList())
    val allInventoryItems: StateFlow<List<InventoryItem>> = _allInventoryItems

    private val _isTagAlreadyAssigned = MutableStateFlow(false)
    val isTagAlreadyAssigned: StateFlow<Boolean> = _isTagAlreadyAssigned

    init {
        observeNfcScans()
    }

    fun checkIfTagExists(tagId: String) {
        viewModelScope.launch {
            val existingItem = inventoryRepository.getItemByTagId(tagId)
            _isTagAlreadyAssigned.value = existingItem != null
        }
    }

    private fun observeNfcScans() {
        viewModelScope.launch {
            Log.d("ScannerViewModel", "Observing NFC scans")
            scannerRepository.nfcScans.collect { tagId ->
                if (tagId != null && _isScanning.value) {
                    _tagId.value = tagId
//                    processScannedTag(tagId)
                }
            }
        }
    }

    fun loadAllInventoryItems() {
        viewModelScope.launch {
            try {
                val items = inventoryRepository.getAllInventoryItems()
                _allInventoryItems.value = items
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load inventory items"
            }
        }
    }

    fun attachTagToItem(tagId: String, itemId: String) {
        viewModelScope.launch {
            try {
                inventoryRepository.associateTagWithItem(itemId, tagId)
                _errorMessage.value = "Tag successfully attached."
                clearTagId() // Optional: hide UI after success
            } catch (e: Exception) {
                _errorMessage.value = "Failed to attach tag: ${e.message}"
            }
        }
    }

    fun clearTagId() {
        // however you're managing tagId, reset it
        _tagId.value = null
    }

    // Update the processScannedTag method
    private fun processScannedTag(tagId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Get the item associated with this tag
                val item = scannerRepository.getItemByTagId(tagId)
                _scannedItem.value = item

                // Record the scan
                scannerRepository.recordScan(item.id, item.name, 1)

                // Check if item is expired or low stock and send signals to ESP32
                if (item.isExpired()) {
                    scannerRepository.triggerBuzzer()
                    scannerRepository.sendAlertToLCD("EXPIRED", "Item: ${item.name}")

                    // Call createExpiredItemAlert from AlertRepository
                    try {
                        val alertRepository = AlertRepository(firestore, auth)
                        alertRepository.createExpiredItemAlert(item)
                    } catch (e: Exception) {
                        Log.e("ScannerViewModel", "Error creating expired item alert", e)
                    }
                }

                if (item.isLowStock()) {
                    scannerRepository.triggerLowStockIndicator()
                    scannerRepository.sendAlertToLCD("LOW STOCK", "Item: ${item.name}")

                    // Call createLowStockAlert from AlertRepository
                    try {
                        val alertRepository = AlertRepository(firestore, auth)
                        alertRepository.createLowStockAlert(item)
                    } catch (e: Exception) {
                        Log.e("ScannerViewModel", "Error creating low stock alert", e)
                    }
                }

                // Stop scanning after successful scan
                _isScanning.value = false
            } catch (e: Exception) {
                _errorMessage.value = "Error processing tag: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Ensure createAlert is correctly defined as a suspend function
//    private suspend fun createAlert(title: String, message: String, type: AlertType, itemId: String) {
//        try {
//            val alertRepository = AlertRepository(firestore, auth)
//            alertRepository.createAlert(title, message, type, itemId)
//        } catch (e: Exception) {
//            Log.e("ScannerViewModel", "Error creating alert", e)
//        }
//    }


    fun startScanning() {
        Log.e("ScannerViewModel", "Starting scanning")
        viewModelScope.launch {
            _isScanning.value = true
            _errorMessage.value = null

            try {
                scannerRepository.startNfcScan()
            } catch (e: Exception) {
                _errorMessage.value = "Error starting NFC scan: ${e.message}"
                _isScanning.value = false
            }
        }
    }

    fun stopScanning() {
        viewModelScope.launch {
            try {
                scannerRepository.stopNfcScan()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearScannedItem() {
        _scannedItem.value = null
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            scannerRepository.stopNfcScan()
        }
    }
}

