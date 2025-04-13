package com.example.inventag.data.repositories

import com.example.inventag.models.Alert
import com.example.inventag.models.AlertType
import com.example.inventag.models.InventoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun getAlerts(): Flow<List<Alert>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val listenerRegistration = firestore.collection("alerts")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val alerts = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(Alert::class.java)?.copy(id = document.id)
                } ?: emptyList()

                trySend(alerts)
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun markAlertAsRead(alertId: String) {
        firestore.collection("alerts")
            .document(alertId)
            .update("isRead", true)
            .await()
    }

    suspend fun markAllAlertsAsRead() {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val unreadAlerts = firestore.collection("alerts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()

        val batch = firestore.batch()
        unreadAlerts.documents.forEach { document ->
            batch.update(document.reference, "isRead", true)
        }

        batch.commit().await()
    }

    // Create an alert for a low stock item
    suspend fun createLowStockAlert(item: InventoryItem) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val alert = Alert(
            title = "Low Stock Alert",
            message = "Item '${item.name}' is running low on stock (${item.quantity} remaining).",
            type = AlertType.LOW_STOCK,
            timestamp = Timestamp.now(),
            isRead = false,
            itemId = item.id,
            userId = userId
        )

        firestore.collection("alerts").add(alert).await()
    }

    // Create an alert for an expired item
    suspend fun createExpiredItemAlert(item: InventoryItem) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val alert = Alert(
            title = "Expired Item Alert",
            message = "Item '${item.name}' has expired.",
            type = AlertType.EXPIRED,
            timestamp = Timestamp.now(),
            isRead = false,
            itemId = item.id,
            userId = userId
        )

        firestore.collection("alerts").add(alert).await()
    }
}

