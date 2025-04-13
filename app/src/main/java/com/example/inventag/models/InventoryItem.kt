package com.example.inventag.models

import com.google.firebase.Timestamp
import java.util.Date

data class InventoryItem(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val price: Double = 0.0,
    val expiryDate: Timestamp? = null,
    val category: String = "",
    val nfcTagId: String? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now()

) {
    fun isExpired(): Boolean {
        return expiryDate?.toDate()?.before(Date()) ?: false
    }

    fun isLowStock(): Boolean {
        return quantity < 5 // Threshold for low stock
    }
}

