package com.example.inventag.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.InventoryRepository
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.usecases.TagProcessingResult
import com.example.inventag.usecases.TagProcessorUseCase
import com.example.inventag.models.InventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


// A sealed class to represent all possible states of the scanner screen
sealed class ScannerUiState {
    object Idle : ScannerUiState()
    object Scanning : ScannerUiState()
    object Loading : ScannerUiState()
    data class Success(val item: InventoryItem, val statusMessage: String) : ScannerUiState()
    data class AssignTag(val tagId: String, val availableItems: List<InventoryItem>) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val scannerRepository: ScannerRepository,
    private val inventoryRepository: InventoryRepository, // Still needed for getUnassignedItems
    private val tagProcessorUseCase: TagProcessorUseCase  // Inject the new use case
) : ViewModel() {

    private val TAG = "ScannerViewModel_BlackBox"

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        observeNfcScans()
    }

    private fun observeNfcScans() {
        viewModelScope.launch {
            scannerRepository.nfcScans.collect { tagId ->
                if (tagId != null && _uiState.value is ScannerUiState.Scanning) {
                    Log.d(TAG, "✅ STEP 1: Tag received from Repository. Tag ID: $tagId")
                    processScannedTag(tagId)
                }
            }
        }
    }

    // This function is now much simpler and delegates logic to the use case
    private fun processScannedTag(tagId: String) {
        viewModelScope.launch {
            Log.d(TAG, "🚀 STEP 2: Starting to process tag ID: $tagId")
            _uiState.value = ScannerUiState.Loading

            // Delegate all the complex logic to the use case
            when (val result = tagProcessorUseCase.processTag(tagId)) {
                is TagProcessingResult.Success -> {
                    Log.d(TAG, "👍 STEP 4: SUCCESS! Tag is KNOWN.")
                    _uiState.value = ScannerUiState.Success(result.item, result.statusMessage)
                }
                is TagProcessingResult.UnknownTag -> {
                    Log.w(TAG, "⚠️ STEP 4: FAILURE! Tag is UNKNOWN. Fetching items for assignment.")
                    val unassignedItems = inventoryRepository.getUnassignedItems()
                    _uiState.value = ScannerUiState.AssignTag(result.tagId, unassignedItems)
                }
                is TagProcessingResult.Error -> {
                    Log.e(TAG, "🔥 CRITICAL ERROR during tag processing: ${result.message}")
                    _uiState.value = ScannerUiState.Error(result.message)
                }
            }
            Log.d(TAG, "🏁 STEP 5: Processing finished for tag ID: $tagId")
        }
    }

    fun assignTagToItem(itemId: String, tagId: String) {
        viewModelScope.launch {
            try {
                inventoryRepository.associateTagWithItem(itemId, tagId)
                _uiState.value = ScannerUiState.Idle
            } catch (e: Exception) {
                _uiState.value = ScannerUiState.Error("Failed to assign tag: ${e.message}")
            }
        }
    }

    fun startScanning() {
        if (_uiState.value is ScannerUiState.Scanning) return
        Log.d(TAG, "▶️ START SCANNING button pressed. Polling for tag...")
        _uiState.value = ScannerUiState.Scanning
        viewModelScope.launch {
            scannerRepository.startNfcScan()
        }
    }

    fun stopScanning() {
        if (_uiState.value is ScannerUiState.Scanning) {
            _uiState.value = ScannerUiState.Idle
            scannerRepository.stopNfcScan()
        }
    }

    fun clearUiState() {
        _uiState.value = ScannerUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        scannerRepository.stopNfcScan()
    }
}