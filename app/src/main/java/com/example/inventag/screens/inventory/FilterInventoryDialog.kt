package com.example.inventag.screens.inventory

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun FilterInventoryDialog(
    onDismiss: () -> Unit,
    onApplyFilter: (category: String?, showExpired: Boolean, showLowStock: Boolean) -> Unit,
    categories: List<String>,
    currentCategory: String?,
    showExpired: Boolean,
    showLowStock: Boolean
) {
    var selectedCategory by remember { mutableStateOf(currentCategory) }
    var includeExpired by remember { mutableStateOf(showExpired) }
    var includeLowStock by remember { mutableStateOf(showLowStock) }

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
                    text = "Filter Inventory",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    Text("All Categories")
                }

                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                        Text(category)
                    }
                }



                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeExpired,
                        onCheckedChange = { includeExpired = it }
                    )
                    Text("Show Expired Items")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeLowStock,
                        onCheckedChange = { includeLowStock = it }
                    )
                    Text("Show Low Stock Items")
                }

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
                            onApplyFilter(selectedCategory, includeExpired, includeLowStock)
                        }
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

