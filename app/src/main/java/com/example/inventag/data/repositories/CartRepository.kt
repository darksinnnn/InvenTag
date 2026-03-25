package com.example.inventag.data.repositories

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.example.inventag.models.CartItem
import com.example.inventag.models.InventoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val inventoryRepository: InventoryRepository
) {

    fun getCartItems(): Flow<List<CartItem>> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        ensureCartExists(userId)
        val listenerRegistration = firestore.collection("carts")
            .document(userId)
            .collection("items")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(CartItem::class.java)?.copy(id = document.id)
                } ?: emptyList()
                trySend(items)
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun addItem(itemId: String, name: String, price: Double, quantity: Int) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        ensureCartExists(userId)
        val existingCartItem = getCartItemByInventoryId(userId, itemId)

        if (existingCartItem != null) {
            val newQuantity = existingCartItem.quantity + quantity
            updateCartItemQuantity(userId, existingCartItem.id, newQuantity)
        } else {
            val cartItem = CartItem(
                inventoryItemId = itemId,
                name = name,
                price = price,
                quantity = quantity
            )
            firestore.collection("carts")
                .document(userId)
                .collection("items")
                .add(cartItem)
                .await()
        }
    }

    suspend fun increaseQuantity(cartItemId: String) {
        val userId = auth.currentUser?.uid ?: return
        val docRef = firestore.collection("carts").document(userId).collection("items").document(cartItemId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentQuantity = snapshot.getLong("quantity")?.toInt() ?: 0
            transaction.update(docRef, "quantity", currentQuantity + 1)
        }.await()
    }

    suspend fun decreaseQuantity(cartItemId: String) {
        val userId = auth.currentUser?.uid ?: return
        val docRef = firestore.collection("carts").document(userId).collection("items").document(cartItemId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentQuantity = snapshot.getLong("quantity")?.toInt() ?: 0
            val newQuantity = currentQuantity - 1
            if (newQuantity > 0) {
                transaction.update(docRef, "quantity", newQuantity)
            } else {
                transaction.delete(docRef) // Remove item if quantity becomes 0
            }
        }.await()
    }

    suspend fun removeItem(cartItemId: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("carts")
            .document(userId)
            .collection("items")
            .document(cartItemId)
            .delete()
            .await()
    }

    private suspend fun updateCartItemQuantity(userId: String, cartItemId: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(cartItemId)
            return
        }
        firestore.collection("carts").document(userId).collection("items").document(cartItemId)
            .update("quantity", quantity).await()
    }

    suspend fun clearCart() {
        val userId = auth.currentUser?.uid ?: return
        val cartItemsCollection = firestore.collection("carts").document(userId).collection("items")
        val cartItems = cartItemsCollection.get().await()
        val batch = firestore.batch()
        for (document in cartItems) {
            batch.delete(document.reference)
        }
        batch.commit().await()
    }

    /**
     * ✅ NEW: Implemented the full checkout logic.
     * This function now runs a transaction to ensure stock is available,
     * updates the inventory, logs a new order, and then clears the cart.
     */
    suspend fun checkout(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val cartItems = getCartItemsAsList(userId)
        if (cartItems.isEmpty()) return false

        try {
            // Use a transaction to safely read and then write inventory quantities
            firestore.runTransaction { transaction ->
                val inventoryUpdates = mutableListOf<Pair<DocumentReference, Int>>()

                // Step 1: Read all items and validate stock within the transaction
                for (cartItem in cartItems) {
                    val invDocRef = firestore.collection("inventory").document(cartItem.inventoryItemId)
                    val invSnapshot = transaction.get(invDocRef)
                    val currentStock = invSnapshot.getLong("quantity")?.toInt() ?: 0

                    if (currentStock < cartItem.quantity) {
                        // Not enough stock, abort transaction by throwing an exception
                        throw FirebaseFirestoreException(
                            "Not enough stock for ${cartItem.name}. Available: $currentStock, Requested: ${cartItem.quantity}",
                            FirebaseFirestoreException.Code.ABORTED
                        )
                    }
                    val newStock = currentStock - cartItem.quantity
                    inventoryUpdates.add(Pair(invDocRef, newStock))
                }

                // Step 2: If all checks pass, apply updates
                for ((docRef, newQuantity) in inventoryUpdates) {
                    transaction.update(docRef, "quantity", newQuantity)
                }
            }.await() // Wait for the transaction to complete

            // Step 3: If transaction was successful, log the order and clear the cart
            logOrder(userId, cartItems)
            clearCart()
            return true

        } catch (e: Exception) {
            // Handle transaction failure (e.g., stock issue, network error)
            Log.e("CartRepository", "Checkout transaction failed", e)
            return false
        }
    }

    // Helper function to get a snapshot of the cart for checkout
    private suspend fun getCartItemsAsList(userId: String): List<CartItem> {
        val result = firestore.collection("carts").document(userId).collection("items")
            .get()
            .await()
        return result.toObjects(CartItem::class.java)
    }

    // ✅ NEW: Private function to create a record of the successful order
    private suspend fun logOrder(userId: String, cartItems: List<CartItem>) {
        val orderItems = cartItems.map { cartItem ->
            mapOf(
                "inventoryItemId" to cartItem.inventoryItemId,
                "name" to cartItem.name,
                "price" to cartItem.price,
                "quantity" to cartItem.quantity
            )
        }

        val newOrder = mapOf(
            "userId" to userId,
            "items" to orderItems,
            "totalAmount" to cartItems.sumOf { it.price * it.quantity },
            "createdAt" to Timestamp.now()
        )

        firestore.collection("orders").add(newOrder).await()
    }

    private suspend fun getCartItemByInventoryId(userId: String, inventoryItemId: String): CartItem? {
        val querySnapshot = firestore.collection("carts").document(userId).collection("items")
            .whereEqualTo("inventoryItemId", inventoryItemId).get().await()
        return if (querySnapshot.documents.isNotEmpty()) {
            val doc = querySnapshot.documents[0]
            doc.toObject(CartItem::class.java)?.copy(id = doc.id)
        } else {
            null
        }
    }

    private suspend fun ensureCartExists(userId: String) {
        val cartDocRef = firestore.collection("carts").document(userId)
        val cartDoc = cartDocRef.get().await()
        if (!cartDoc.exists()) {
            cartDocRef.set(mapOf("userId" to userId, "createdAt" to Timestamp.now())).await()
        }
    }
}