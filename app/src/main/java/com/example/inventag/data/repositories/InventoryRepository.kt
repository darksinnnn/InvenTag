package com.example.inventag.data.repositories

import com.example.inventag.models.InventoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun getInventoryItems(): Flow<List<InventoryItem>> = callbackFlow {
        val listenerRegistration = firestore.collection("inventory")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val items = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(InventoryItem::class.java)?.copy(id = document.id)
                } ?: emptyList()

                trySend(items)
            }

        awaitClose { listenerRegistration.remove() }
    }

    suspend fun getAllInventoryItems(): List<InventoryItem> {
        return firestore.collection("inventory")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(InventoryItem::class.java)?.copy(id = document.id)
            }
    }

    fun getCategories(): Flow<List<String>> = callbackFlow {
        val listenerRegistration = firestore.collection("inventory")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val categories = snapshot?.documents?.mapNotNull { document ->
                    document.getString("category")
                }?.distinct() ?: emptyList()

                trySend(categories)
            }

        awaitClose { listenerRegistration.remove() }
    }

    // Update the addInventoryItem method
    suspend fun addInventoryItem(name: String, quantity: Int, expiryDate: Date?, category: String) {
        // Store the creator's userId for reference but don't filter by it
        val userId = auth.currentUser?.uid

        val item = hashMapOf(
            "name" to name,
            "quantity" to quantity,
            "category" to category,
            "createdBy" to userId,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        // Only add expiryDate if it's not null
        if (expiryDate != null) {
            item["expiryDate"] = Timestamp(expiryDate)
        }

        firestore.collection("inventory").add(item).await()
    }

    // Update the updateInventoryItem method
    suspend fun addInventoryItemAndGetId(name: String, quantity: Int, expiryDate: Date?, category: String): String {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val item = hashMapOf(
            "name" to name,
            "quantity" to quantity,
            "category" to category,
            "userId" to userId,
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
            "nfcTagId" to null
        )

        // Only add expiryDate if it's not null
        if (expiryDate != null) {
            item["expiryDate"] = Timestamp(expiryDate)
        }

        val docRef = firestore.collection("inventory").add(item).await()
        return docRef.id
    }


    suspend fun deleteInventoryItem(id: String) {
        firestore.collection("inventory").document(id).delete().await()
    }

    suspend fun associateTagWithItem(itemId: String, tagId: String) {

        firestore.collection("inventory")
            .document(itemId)
            .update("nfcTagId", tagId)
            .await()
    }

    suspend fun getItemByTagId(tagId: String): InventoryItem? {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val querySnapshot = firestore.collection("inventory")
            .whereEqualTo("createdBy", userId)
            .whereEqualTo("nfcTagId", tagId)
            .get()
            .await()

        return if (querySnapshot.documents.isNotEmpty()) {
            val document = querySnapshot.documents[0]
            document.toObject(InventoryItem::class.java)?.copy(id = document.id)
        } else {
            null
        }
    }

    suspend fun updateInventoryItem(id: String, name: String, quantity: Int, expiryDate: Date?, category: String) {
        val userId = auth.currentUser?.uid

        val updates: MutableMap<String, Any> = mutableMapOf(
            "name" to name,
            "quantity" to quantity,
            "category" to category,
            "lastUpdatedBy" to userId!!,
            "updatedAt" to Timestamp.now()
        )

        // If expiryDate is provided, update it; otherwise, delete the existing field
        if (expiryDate != null) {
            updates["expiryDate"] = Timestamp(expiryDate)
        } else {
            updates["expiryDate"] = com.google.firebase.firestore.FieldValue.delete()
        }

        firestore.collection("inventory").document(id).update(updates).await()
    }

    suspend fun decreaseItemQuantity(itemId: String, amount: Int = 1) {
        val docRef = firestore.collection("inventory").document(itemId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentQuantity = snapshot.getLong("quantity")?.toInt() ?: 0
            val newQuantity = maxOf(0, currentQuantity - amount)

            transaction.update(docRef, "quantity", newQuantity)
            transaction.update(docRef, "updatedAt", Timestamp.now())
        }.await()
    }

    suspend fun getItemByItemId(itemId: String): InventoryItem? {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val documentSnapshot = firestore.collection("inventory")
            .document(itemId)
            .get()
            .await()

        return if (documentSnapshot.exists()) {
            documentSnapshot.toObject(InventoryItem::class.java)?.copy(id = documentSnapshot.id)
        } else {
            null
        }
    }

}