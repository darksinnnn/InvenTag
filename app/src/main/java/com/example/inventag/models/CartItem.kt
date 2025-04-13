package com.example.inventag.models

import com.google.firebase.Timestamp

data class CartItem(
    val id: String = "",
    val inventoryItemId: String = "",
    val name: String = "",
    val quantity: Int = 0,
    val createdAt: Timestamp = Timestamp.now(),
    val price: Double = 0.0
)

