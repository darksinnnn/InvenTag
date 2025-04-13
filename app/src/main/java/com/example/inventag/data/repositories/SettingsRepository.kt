package com.example.inventag.data.repositories

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _esp32IpAddress = MutableStateFlow(getStoredEsp32IpAddress())

    fun getEsp32IpAddress(): Flow<String> = _esp32IpAddress.asStateFlow()

    fun saveEsp32IpAddress(ipAddress: String) {
        preferences.edit().putString(ESP32_IP_KEY, ipAddress).apply()
        _esp32IpAddress.value = ipAddress
    }

    private fun getStoredEsp32IpAddress(): String {
        return preferences.getString(ESP32_IP_KEY, DEFAULT_ESP32_IP) ?: DEFAULT_ESP32_IP
    }

    companion object {
        private const val ESP32_IP_KEY = "esp32_ip_address"
        private const val DEFAULT_ESP32_IP = "10.0.2.2:9090" // Update this to match your ESP32's default IP
    }
}

