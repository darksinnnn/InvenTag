package com.example.inventag.data.repositories

import android.util.Log
import com.example.inventag.models.InventoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    /**
     * Gets a real-time stream of ALL inventory items for a shared system.
     */
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
                trySend(items).isSuccess
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Fetches ALL inventory items once.
     */
    suspend fun getAllInventoryItems(): List<InventoryItem> {
        return firestore.collection("inventory")
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(InventoryItem::class.java)?.copy(id = document.id)
            }
    }

    /**
     * Gets a real-time stream of ALL unique categories in the inventory.
     */
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
                trySend(categories).isSuccess
            }
        awaitClose { listenerRegistration.remove() }
    }

    /**
     * Fetches a list of items that do not have an NFC tag associated with them yet.
     */
    suspend fun getUnassignedItems(): List<InventoryItem> {
        return firestore.collection("inventory")
            .whereEqualTo("nfcTagId", null)
            .get()
            .await()
            .documents
            .mapNotNull { document ->
                document.toObject(InventoryItem::class.java)?.copy(id = document.id)
            }
    }

    /**
     * Adds a new inventory item. We still record who created it.
     * ✅ FIXED: Added 'price: Double?' to the function signature.
     */
    suspend fun addInventoryItem(name: String, quantity: Int, expiryDate: Date?, category: String, price: Double?): String {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in to add items")

        val item = hashMapOf<String, Any?>(
            "name" to name,
            "quantity" to quantity,
            "category" to category,
            "price" to price,
            "createdBy" to userId, // KEPT: Important for auditing
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now(),
            "nfcTagId" to null
        )

        if (expiryDate != null) {
            item["expiryDate"] = Timestamp(expiryDate)
        }

        val docRef = firestore.collection("inventory").add(item).await()
        return docRef.id
    }

    /**
     * Deletes an inventory item. Any logged-in user can delete.
     */
    suspend fun deleteInventoryItem(id: String) {
        firestore.collection("inventory").document(id).delete().await()
    }

    /**
     * Associates an NFC tag ID with an inventory item. Any logged-in user can do this.
     */
    suspend fun associateTagWithItem(itemId: String, tagId: String) {
        val docRef = firestore.collection("inventory").document(itemId)
        docRef.update("nfcTagId", tagId).await()
    }

    /**
     * Retrieves a single inventory item by its associated NFC tag ID, regardless of creator.
     */
    suspend fun getItemByTagId(tagId: String): InventoryItem? {
        Log.d("RepoDebug", "Querying for tag '$tagId' (shared inventory)")
        val querySnapshot = firestore.collection("inventory")
            .whereEqualTo("nfcTagId", tagId)
            .limit(1)
            .get()
            .await()

        return if (querySnapshot.documents.isNotEmpty()) {
            val document = querySnapshot.documents[0]
            document.toObject(InventoryItem::class.java)?.copy(id = document.id)
        } else {
            null
        }
    }

    /**
     * Updates an inventory item's details. Any logged-in user can update.
     * ✅ FIXED: Added 'price: Double?' to the function signature.
     */
    suspend fun updateInventoryItem(id: String, name: String, quantity: Int, expiryDate: Date?, category: String, price: Double?) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User must be logged in to update items")
        val docRef = firestore.collection("inventory").document(id)

        val updates: MutableMap<String, Any?> = mutableMapOf(
            "name" to name,
            "quantity" to quantity,
            "category" to category,
            "price" to price,
            "lastUpdatedBy" to userId, // KEPT: Important for auditing
            "updatedAt" to Timestamp.now()
        )

        updates["expiryDate"] = expiryDate?.let { Timestamp(it) } ?: FieldValue.delete()
        docRef.update(updates).await()
    }

    /**
     * Decreases an item's quantity. Any logged-in user can do this.
     */
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

    /**
     * Retrieves a single inventory item by its ID.
     */
    suspend fun getItemByItemId(itemId: String): InventoryItem? {
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