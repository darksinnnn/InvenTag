package com.example.inventag.models

import com.google.firebase.Timestamp

data class Alert(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: AlertType = AlertType.INFO,
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val itemId: String? = null,
    val userId: String = ""
)

enum class AlertType {
    LOW_STOCK, EXPIRED, INFO, WARNING
}
