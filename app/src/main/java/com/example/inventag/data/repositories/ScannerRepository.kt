package com.example.inventag.data.repositories

import android.content.Context
import android.util.Log
import com.example.inventag.models.InventoryItem
import com.example.inventag.models.ScanRecord
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val settingsRepository: SettingsRepository
) {

    private val _nfcScans = MutableStateFlow<String?>(null)
    val nfcScans: StateFlow<String?> = _nfcScans

    private var scanJob: Job? = null

    private suspend fun getEsp32IpAddress(): String {
        return settingsRepository.getEsp32IpAddress().firstOrNull() ?: "192.168.1.100" // Default fallback
    }

    fun startNfcScan() {
        if (scanJob?.isActive == true) return
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    Log.d("ScannerRepository", "Polling for NFC tag...")
                    val tagId = pollForNfcTag()

                    if (tagId != null) {
                        _nfcScans.value = tagId
                        delay(1500)
                        _nfcScans.value = null
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        Log.d("ScannerRepository", "NFC polling was cancelled.")
                        throw e
                    }
                    Log.e("ScannerRepository", "Error while polling for NFC tag", e)
                }
                delay(500)
            }
        }
    }

    fun stopNfcScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun pollForNfcTag(): String? = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getEsp32IpAddress()
            val url = URL("http://$ipAddress/nfc/read")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            Log.d("ScannerRepository", "Polling from IP: $ipAddress")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonObject = JSONObject(response.toString())
                val tagId = jsonObject.optString("tagId")

                if (!tagId.isNullOrEmpty() && tagId != "null") {
                    return@withContext tagId
                }
            }

            connection.disconnect()
            return@withContext null
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error polling ESP32 for NFC tag", e)
            return@withContext null
        }
    }

    // ✅ THIS IS THE CORRECTED FUNCTION
    suspend fun getItemByTagId(tagId: String): InventoryItem? { // <-- Return nullable InventoryItem?
        val tagDocument = firestore.collection("nfc_tags")
            .whereEqualTo("tagId", tagId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        return if (tagDocument != null) {
            val itemId = tagDocument.getString("itemId")
            if (itemId != null) {
                firestore.collection("inventory").document(itemId).get().await()
                    .toObject(InventoryItem::class.java)?.copy(id = itemId)
            } else {
                null
            }
        } else {
            null // <-- Return null if the tag is not found
        }
    }

    suspend fun recordScan(itemId: String, itemName: String, quantity: Int) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val userName = auth.currentUser?.displayName ?: "Unknown User"

        val scanRecord = ScanRecord(
            itemId = itemId,
            itemName = itemName,
            quantity = quantity,
            scanDate = Timestamp.now(),
            isValid = true,
            userId = userId,
            userName = userName
        )

        firestore.collection("scans").add(scanRecord).await()
    }

    suspend fun triggerBuzzer() = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getEsp32IpAddress()
            val url = URL("http://$ipAddress/buzzer?duration=800")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error triggering buzzer", e)
        }
    }

    suspend fun triggerLowStockIndicator() = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getEsp32IpAddress()
            val url = URL("http://$ipAddress/led?color=red&state=on")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error triggering LED", e)
        }
    }

    suspend fun sendAlertToLCD(type: String, message: String) {
        try {
            val ipAddress = getEsp32IpAddress()
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val encodedType = URLEncoder.encode(type, "UTF-8")
            val url = URL("http://$ipAddress/alert?type=$encodedType&message=$encodedMessage")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()
            connection.responseCode
            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error sending alert to LCD", e)
        }
    }
}