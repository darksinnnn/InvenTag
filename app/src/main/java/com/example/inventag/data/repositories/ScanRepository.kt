package com.example.inventag.data.repositories

import com.example.inventag.models.ScanRecord
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    fun getScanRecords(): Flow<List<ScanRecord>> = callbackFlow {
        // Get all scan records for all users
        val listenerRegistration = firestore.collection("scans")
            .orderBy("scanDate", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val records = snapshot?.documents?.mapNotNull { document ->
                    document.toObject(ScanRecord::class.java)?.copy(id = document.id)
                } ?: emptyList()

                trySend(records)
            }

        awaitClose { listenerRegistration.remove() }
    }

    fun getFilteredScanRecords(startDate: Date?, endDate: Date?, isValid: Boolean?): Flow<List<ScanRecord>> = callbackFlow {
        // Declare query as Query explicitly
        var query: Query = firestore.collection("scans")

        if (startDate != null) {
            query = query.whereGreaterThanOrEqualTo("scanDate", Timestamp(startDate))
        }

        if (endDate != null) {
            query = query.whereLessThanOrEqualTo("scanDate", Timestamp(endDate))
        }

        if (isValid != null) {
            query = query.whereEqualTo("isValid", isValid)
        }

        // Ensure at least one filter is applied before ordering
        query = if (startDate != null || endDate != null || isValid != null) {
            query.orderBy("scanDate", Query.Direction.DESCENDING)
        } else {
            query
        }

        val listenerRegistration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val records = snapshot?.documents?.mapNotNull { document ->
                document.toObject(ScanRecord::class.java)?.copy(id = document.id)
            } ?: emptyList()

            trySend(records)
        }

        awaitClose { listenerRegistration.remove() }
    }
}
