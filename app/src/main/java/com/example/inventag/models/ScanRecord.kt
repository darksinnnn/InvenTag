package com.example.inventag.models

import com.google.firebase.Timestamp

data class ScanRecord(
    val id: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val quantity: Int = 0,
    val scanDate: Timestamp = Timestamp.now(),
    val isValid: Boolean = true,
    val userId: String = "",
    val userName: String = "" // Added userName field for better tracking
)