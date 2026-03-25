package com.example.inventag.usecases

import com.example.inventag.data.repositories.AlertRepository
import com.example.inventag.data.repositories.InventoryRepository
import com.example.inventag.data.repositories.ScannerRepository
import com.example.inventag.models.InventoryItem
import javax.inject.Inject
import javax.inject.Singleton

sealed class TagProcessingResult {
    data class Success(val item: InventoryItem, val statusMessage: String) : TagProcessingResult()
    data class UnknownTag(val tagId: String) : TagProcessingResult()
    data class Error(val message: String) : TagProcessingResult()
}

@Singleton
class TagProcessorUseCase @Inject constructor(
    private val inventoryRepository: InventoryRepository,
    private val scannerRepository: ScannerRepository,
    private val alertRepository: AlertRepository
) {
    suspend fun processTag(tagId: String): TagProcessingResult {
        return try {
            val item = inventoryRepository.getItemByTagId(tagId)

            if (item != null) {
                // ✅ REMOVED: scannerRepository.recordScan(...) is no longer called here.
                val statusMessage = when {
                    item.isExpired() -> {
                        scannerRepository.triggerBuzzer()
                        scannerRepository.sendAlertToLCD("EXPIRED", item.name)
                        alertRepository.createExpiredItemAlert(item)
                        "ALERT: Item '${item.name}' has EXPIRED!"
                    }
                    item.isLowStock() -> {
                        scannerRepository.triggerLowStockIndicator()
                        scannerRepository.sendAlertToLCD("LOW STOCK", item.name)
                        alertRepository.createLowStockAlert(item)
                        "WARNING: '${item.name}' is low on stock."
                    }
                    else -> {
                        scannerRepository.sendAlertToLCD("OK", item.name)
                        "SUCCESS: Item '${item.name}' is OK."
                    }
                }
                TagProcessingResult.Success(item, statusMessage)
            } else {
                TagProcessingResult.UnknownTag(tagId)
            }
        } catch (e: Exception) {
            TagProcessingResult.Error("An error occurred: ${e.message}")
        }
    }
}