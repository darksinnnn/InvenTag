package com.example.inventag.viewmodels

import android.util.Log
import com.example.inventag.data.repositories.InventoryRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.CartRepository
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.models.CartItem
import com.example.inventag.models.InventoryItem
import com.example.inventag.screens.auth.LoginScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val inventoryRepository: InventoryRepository,
    private val scannerRepository: ScannerRepository
) : ViewModel() {

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _totalAmount = MutableStateFlow(0.0)
    val totalAmount: StateFlow<Double> = _totalAmount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadCartItems()

        // Observe NFC scans
        viewModelScope.launch {
            scannerRepository.nfcScans.collect { tagId ->
                if (tagId != null && _isScanning.value) {
                    addItemFromNfcTag(tagId)
                    _isScanning.value = false
                }
            }
        }
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

    private fun addItemFromNfcTag(tagId: String) {
        viewModelScope.launch {
            try {
                val item = inventoryRepository.getItemByTagId(tagId)
                if (item != null) {
                    cartRepository.addItem(item.id, item.name, 19.99, 1) // Default price for demo
                } else {
                    Log.e("CartViewModel", "Item not found for NFC tag: $tagId")
                    _errorMessage.value = "Item not found for NFC tag: $tagId"
                }
            } catch (e: Exception) {
               Log.e("CartViewModel", "Error adding item from NFC tag", e)
            }
        }
    }

    fun checkout() {
        viewModelScope.launch {
            _isLoading.value = true

            // Update inventory quantities
            for (cartItem in _cartItems.value) {
                val inventoryItem =  inventoryRepository.getItemByItemId(cartItem.inventoryItemId)
                if (inventoryItem != null) {
                    val isExpired = inventoryItem.expiryDate?.toDate()?.before(Date()) == true
                    val isLowStock = inventoryItem.quantity < 5
                    if (isExpired) {
                        scannerRepository.triggerBuzzer()
                    } else if (isLowStock) {
                        scannerRepository.triggerLowStockIndicator()
                    }
                }
                inventoryRepository.decreaseItemQuantity(cartItem.inventoryItemId, cartItem.quantity)
            }

            // Clear the cart
            cartRepository.clearCart()

            _isLoading.value = false
        }
    }


}