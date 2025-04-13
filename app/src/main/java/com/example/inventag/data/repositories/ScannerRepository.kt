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

    private var isScanning = false
    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.IO + scanJob)

    // Function to get ESP32 IP Address properly
    private suspend fun getEsp32IpAddress(): String {
        return settingsRepository.getEsp32IpAddress().firstOrNull() ?: "192.168.1.100" // Default fallback
    }

    fun startNfcScan() {
        if (isScanning) return
        isScanning = true

        scanScope.launch {
            try {
                var retry_count=10;
                while (retry_count>0) {
                    try {
                        Log.d("ScannerRepository", "Polling for NFC tag")
                        val tagId = pollForNfcTag()

                        if (tagId != null) {
                            _nfcScans.value = tagId
                            delay(500)
                            break
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("ScannerRepository", "Error polling for NFC tag", e)
                    }
                    retry_count--
                    delay(500)
                }
            } catch (e: Exception) {
                Log.d("ScannerRepository", "NFC scanning coroutine ended")
            }
        }
//        isScanning = false
    }

    fun stopNfcScan() {
        isScanning = false
        scanJob.cancel()
    }

    private suspend fun pollForNfcTag(): String? = withContext(Dispatchers.IO) {
        try {
            val ipAddress = getEsp32IpAddress()
            val url = URL("http://$ipAddress/nfc/read")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
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
                val tagId = jsonObject.optString("tagId", null)

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

    suspend fun getItemByTagId(tagId: String): InventoryItem {
        return firestore.collection("nfc_tags")
            .whereEqualTo("tagId", tagId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.let { tagDoc ->
                val itemId = tagDoc.getString("itemId") ?: throw IllegalStateException("No associated item")
                firestore.collection("inventory").document(itemId).get().await().toObject(InventoryItem::class.java)
                    ?.copy(id = itemId) ?: throw IllegalStateException("Failed to retrieve inventory item")
            }
            ?: throw IllegalStateException("Tag not found")
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
            val url = URL("http://$ipAddress/buzzer?duration=3000")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("ScannerRepository", "Failed to trigger buzzer: HTTP ${connection.responseCode}")
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error triggering buzzer", e)
        }
    }

    suspend fun triggerLowStockIndicator() {
        try {
            val ipAddress = getEsp32IpAddress()
            val url = URL("http://$ipAddress/led?color=red&state=on")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("ScannerRepository", "Failed to trigger LED: HTTP ${connection.responseCode}")
            }

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

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("ScannerRepository", "Failed to send alert to LCD: HTTP ${connection.responseCode}")
            }

            connection.disconnect()
        } catch (e: Exception) {
            Log.e("ScannerRepository", "Error sending alert to LCD", e)
        }
    }
}
