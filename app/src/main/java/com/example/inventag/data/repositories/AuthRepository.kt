package com.example.inventag.data.repositories

import com.example.inventag.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signup(name: String, email: String, password: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { firebaseUser ->
            // Update display name
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // Create user document in Firestore
            val user = User(
                id = firebaseUser.uid,
                name = name,
                email = email,
                photoUrl = null
            )
            firestore.collection("users")
                .document(firebaseUser.uid)
                .set(user)
                .await()
        }
    }

    suspend fun logout() {
        auth.signOut()
    }

    suspend fun updateProfile(name: String, photoUrl: String?) {
        val user = getCurrentUser() ?: throw IllegalStateException("User not logged in")

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(name)
            .apply {
                photoUrl?.let { setPhotoUri(android.net.Uri.parse(it)) }
            }
            .build()

        user.updateProfile(profileUpdates).await()

        // Update Firestore document
        firestore.collection("users")
            .document(user.uid)
            .update(
                mapOf(
                    "name" to name,
                    "photoUrl" to photoUrl
                )
            )
            .await()
    }
}

