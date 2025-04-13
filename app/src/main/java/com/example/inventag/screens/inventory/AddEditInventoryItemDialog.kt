package com.example.inventag.screens.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.inventag.models.InventoryItem
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc

// Update the dialog to include NFC tag association
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInventoryItemDialog(
    item: InventoryItem? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: Int, expiryDate: Date?, category: String) -> Unit,
    onScanNfcTag: () -> Unit
) {
    val isEditing = item != null
    val title = if (isEditing) "Edit Item" else "Add New Item"

    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantityText by remember { mutableStateOf(item?.quantity?.toString() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "") }
    var hasExpiryDate by remember { mutableStateOf(item?.expiryDate != null) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var expiryDateText by remember {
        mutableStateOf(
            item?.expiryDate?.toDate()?.let { dateFormat.format(it) } ?:
            dateFormat.format(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // Default to 30 days from now
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        if (it.isEmpty() || it.toIntOrNull() != null) {
                            quantityText = it
                        }
                    },
                    label = { Text("Quantity") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Add checkbox for expiry date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = hasExpiryDate,
                        onCheckedChange = { hasExpiryDate = it }
                    )
                    Text("Has Expiry Date")
                }

                // Only show expiry date field if hasExpiryDate is true
                if (hasExpiryDate) {
                    OutlinedTextField(
                        value = expiryDateText,
                        onValueChange = { },
                        label = { Text("Expiry Date") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            TextButton(onClick = { showDatePicker = true }) {
                                Text("Select")
                            }
                        }
                    )
                }

                // Add NFC tag association button
//                Button(
//                    onClick = onScanNfcTag,
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = MaterialTheme.colorScheme.secondary
//                    )
//                ) {
//                    Icon(
//                        imageVector = Icons.Default.Nfc,
//                        contentDescription = null,
//                        modifier = Modifier.size(24.dp)
//                    )
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Text("Scan NFC Tag to Associate")
//                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val quantity = quantityText.toIntOrNull() ?: 0
                            val expiryDate = if (hasExpiryDate) {
                                try {
                                    dateFormat.parse(expiryDateText)
                                } catch (e: Exception) {
                                    null
                                }
                            } else null

                            onSave(name, quantity, expiryDate, category)
                        },
                        enabled = name.isNotBlank() && quantityText.isNotBlank() && category.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showDatePicker && hasExpiryDate) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            expiryDateText = dateFormat.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}