package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.CartRepository
import com.example.inventag.usecases.TagProcessingResult
import com.example.inventag.usecases.TagProcessorUseCase
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.models.CartItem
import com.example.inventag.models.InventoryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val scannerRepository: ScannerRepository,
    private val tagProcessorUseCase: TagProcessorUseCase
) : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()

    private val _alertMessage = MutableStateFlow<String?>(null)
    val alertMessage: StateFlow<String?> = _alertMessage.asStateFlow()

    init {
        loadCartItems()
        // ✅ NEW: Start listening for NFC scans as soon as the ViewModel is created.
        observeNfcScansForCart()
    }

    // ✅ NEW: This function listens for tag detections from the shared ScannerRepository.
    private fun observeNfcScansForCart() {
        viewModelScope.launch {
            scannerRepository.nfcScans.collect { tagId ->
                // Process any tag that is detected, as long as scanning is active.
                if (tagId != null && _isScanning.value) {
                    handleScannedItem(tagId)
                }
            }
        }
    }

    private fun handleScannedItem(tagId: String) {
        viewModelScope.launch {
            when (val result = tagProcessorUseCase.processTag(tagId)) {
                is TagProcessingResult.Success -> {
                    addItemToCart(result.item)
                    // The hardware alerts are handled by the use case.
                    // We just set a message for the UI.
                    if (result.item.isExpired() || result.item.isLowStock()) {
                        _alertMessage.value = result.statusMessage
                    }
                }
                is TagProcessingResult.UnknownTag -> {
                    _alertMessage.value = "This tag is not assigned to any item."
                }
                is TagProcessingResult.Error -> {
                    _alertMessage.value = result.message
                }
                // ✅ FIXED: Added the required 'else' branch to make the 'when' exhaustive.
                else -> {
                    // This case should not happen with the current sealed class,
                    // but it satisfies the compiler.
                }
            }
        }
    }

    private fun addItemToCart(item: InventoryItem) {
        viewModelScope.launch {
            // Assuming a demo price since it's not in the InventoryItem model.
            // You might want to add a price field to your InventoryItem model.
            cartRepository.addItem(item.id, item.name, 19.99, 1)
        }
    }

    fun onAlertMessageShown() {
        _alertMessage.value = null
    }

    private fun loadCartItems() {
        viewModelScope.launch {
            _isLoading.value = true
            cartRepository.getCartItems().collect { items ->
                _cartItems.value = items
                calculateTotal()
                _isLoading.value = false
            }
        }
    }

    private fun calculateTotal() {
        _totalAmount.value = _cartItems.value.sumOf { it.price * it.quantity }
    }

    fun increaseQuantity(itemId: String) {
        viewModelScope.launch {
            cartRepository.increaseQuantity(itemId)
        }
    }

    fun decreaseQuantity(itemId: String) {
        viewModelScope.launch {
            cartRepository.decreaseQuantity(itemId)
        }
    }

    fun removeItem(itemId: String) {
        viewModelScope.launch {
            cartRepository.removeItem(itemId)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            cartRepository.clearCart()
        }
    }

    fun startNfcScan() {
        if (_isScanning.value) return
        _isScanning.value = true
        scannerRepository.startNfcScan()
    }

    fun stopNfcScan() {
        _isScanning.value = false
        scannerRepository.stopNfcScan()
    }

    fun checkout() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val checkoutSuccess = cartRepository.checkout()
                if (!checkoutSuccess) {
                    _alertMessage.value = "Checkout failed. Some items may be out of stock."
                }
            } catch (e: Exception) {
                _alertMessage.value = "An error occurred during checkout."
            } finally {
                _isLoading.value = false
            }
        }
    }
}