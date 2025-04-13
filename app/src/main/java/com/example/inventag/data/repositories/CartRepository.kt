package com.example.inventag.data.repositories

import com.example.inventag.models.CartItem
import com.example.inventag.models.InventoryItem
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    // Get the current user's cart items
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

        // Ensure the cart document exists
        ensureCartExists(userId)

        // Check if the item already exists in the cart
        val existingCartItem = getCartItem(userId, itemId)

        if (existingCartItem != null) {
            // Update the quantity
            val newQuantity = existingCartItem.quantity + quantity
            updateCartItemQuantity(userId, existingCartItem.id, newQuantity)
        } else {
            // Add as a new item
            val cartItem = hashMapOf(
                "inventoryItemId" to itemId,
                "name" to name,
                "price" to price,  // Ensure price is included
                "quantity" to quantity,
                "createdAt" to Timestamp.now()
            )

            firestore.collection("carts")
                .document(userId)
                .collection("items")
                .add(cartItem)
                .await()
        }
    }

    // Add an item to the cart
    suspend fun addToCart(itemId: String, quantity: Int = 1) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        ensureCartExists(userId)

        val inventoryItem = getInventoryItem(itemId)

        val existingCartItem = getCartItem(userId, itemId)

        if (existingCartItem != null) {
            val newQuantity = existingCartItem.quantity + quantity
            updateCartItemQuantity(userId, existingCartItem.id, newQuantity)
        } else {
            val cartItem = hashMapOf(
                "inventoryItemId" to itemId,
                "name" to inventoryItem.name,
                "price" to inventoryItem.price, // Ensure price is stored
                "quantity" to quantity,
                "createdAt" to Timestamp.now()
            )

            firestore.collection("carts")
                .document(userId)
                .collection("items")
                .add(cartItem)
                .await()
        }
    }

    // Increase quantity of an item in the cart
    suspend fun increaseQuantity(itemId: String) {
        val userId = auth.currentUser?.uid ?: return
        val existingCartItem = getCartItem(userId, itemId)
        existingCartItem?.let {
            updateCartItemQuantity(userId, it.id, it.quantity + 1)
        }
    }

    // Decrease quantity of an item in the cart
    suspend fun decreaseQuantity(itemId: String) {
        val userId = auth.currentUser?.uid ?: return
        val existingCartItem = getCartItem(userId, itemId)
        existingCartItem?.let {
            updateCartItemQuantity(userId, it.id, it.quantity - 1)
        }
    }

    // Remove an item from the cart
    suspend fun removeItem(itemId: String) {
        val userId = auth.currentUser?.uid ?: return
        val existingCartItem = getCartItem(userId, itemId)
        existingCartItem?.let {
            removeFromCart(it.id)
        }
    }

    // Remove an item from the cart by its cart ID
    private suspend fun removeFromCart(cartItemId: String) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("carts")
            .document(userId)
            .collection("items")
            .document(cartItemId)
            .delete()
            .await()
    }

    // Update the quantity of an item in the cart
    private suspend fun updateCartItemQuantity(userId: String, cartItemId: String, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(cartItemId)
            return
        }

        firestore.collection("carts")
            .document(userId)
            .collection("items")
            .document(cartItemId)
            .update("quantity", quantity)
            .await()
    }

    // Clear the entire cart
    suspend fun clearCart() {
        val userId = auth.currentUser?.uid ?: return

        val cartItems = firestore.collection("carts")
            .document(userId)
            .collection("items")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(CartItem::class.java)?.copy(id = it.id) }

        val batch = firestore.batch()
        cartItems.forEach { item ->
            val docRef = firestore.collection("carts")
                .document(userId)
                .collection("items")
                .document(item.id)
            batch.delete(docRef)
        }

        batch.commit().await()
    }

    // Checkout - process the cart and update inventory
    suspend fun checkout(): Boolean {
        val userId = auth.currentUser?.uid ?: return false

        val cartItems = firestore.collection("carts")
            .document(userId)
            .collection("items")
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(CartItem::class.java)?.copy(id = it.id) }

        if (cartItems.isEmpty()) {
            return false
        }

        for (item in cartItems) {
            try {
                inventoryRepository.decreaseItemQuantity(item.inventoryItemId, item.quantity)

                val updatedItem = getInventoryItem(item.inventoryItemId)
                if (updatedItem.isLowStock() || updatedItem.isExpired()) {
                    createAlert(updatedItem)
                }
            } catch (e: Exception) {
                return false
            }
        }

        clearCart()
        return true
    }

    // Helper method to get an inventory item
    private suspend fun getInventoryItem(itemId: String): InventoryItem {
        return firestore.collection("inventory")
            .document(itemId)
            .get()
            .await()
            .toObject(InventoryItem::class.java)?.copy(id = itemId)
            ?: throw IllegalStateException("Inventory item not found")
    }

    // Helper method to get a cart item
    private suspend fun getCartItem(userId: String, inventoryItemId: String): CartItem? {
        val querySnapshot = firestore.collection("carts")
            .document(userId)
            .collection("items")
            .whereEqualTo("inventoryItemId", inventoryItemId)
            .get()
            .await()

        return if (querySnapshot.documents.isNotEmpty()) {
            val doc = querySnapshot.documents[0]
            doc.toObject(CartItem::class.java)?.copy(id = doc.id)
        } else {
            null
        }
    }

    // Ensure the cart document exists
    private suspend fun ensureCartExists(userId: String) {
        val cartDocRef = firestore.collection("carts").document(userId)
        val cartDoc = cartDocRef.get().await()

        if (!cartDoc.exists()) {
            cartDocRef.set(mapOf(
                "userId" to userId,
                "createdAt" to Timestamp.now()
            )).await()
        }
    }

    // Create an alert for low stock or expired items
    private suspend fun createAlert(item: InventoryItem) {
        val alertsRepository = AlertRepository(firestore, auth)
        if (item.isExpired()) {
            alertsRepository.createExpiredItemAlert(item)
        } else if (item.isLowStock()) {
            alertsRepository.createLowStockAlert(item)
        }
    }
}
