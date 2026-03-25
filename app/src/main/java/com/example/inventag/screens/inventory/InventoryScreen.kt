package com.example.inventag.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.inventag.ui.components.BottomNavBar
import com.example.inventag.viewmodels.AddItemEvent
import com.example.inventag.viewmodels.InventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    var assignTagDialogState by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.addItemEvent.collect { event ->
            when (event) {
                is AddItemEvent.Success -> {
                    assignTagDialogState = Pair(event.itemId, event.itemName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Inventory") },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        },
        bottomBar = {
            BottomNavBar(navController = navController)
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (inventoryItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No inventory items yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap the + button to add your first item",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(inventoryItems) { item ->
                    InventoryItemCard(
                        item = item,
                        onEditClick = { viewModel.selectItemForEdit(item) },
                        onDeleteClick = { viewModel.deleteItem(item.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditInventoryItemDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, quantity, expiryDate, category, price ->
                viewModel.addItem(name, quantity, expiryDate, category, price)
                showAddDialog = false
            }
        )
    }

    selectedItem?.let { item ->
        AddEditInventoryItemDialog(
            item = item,
            onDismiss = { viewModel.clearSelectedItem() },
            onSave = { name, quantity, expiryDate, category, price ->
                viewModel.updateItem(item.id, name, quantity, expiryDate, category, price)
                viewModel.clearSelectedItem()
            },
            onScanNfcTag = {
                navController.navigate("scanner/${item.id}")
                viewModel.clearSelectedItem()
            }
        )
    }

    if (showFilterDialog) {
        FilterInventoryDialog(
            onDismiss = { showFilterDialog = false },
            onApplyFilter = { category, showExpired, showLowStock ->
                viewModel.applyFilters(category, showExpired, showLowStock)
                showFilterDialog = false
            },
            categories = categories,
            currentCategory = currentFilter.category,
            showExpired = currentFilter.showExpired,
            showLowStock = currentFilter.showLowStock
        )
    }

    assignTagDialogState?.let { (itemId, itemName) ->
        AlertDialog(
            onDismissRequest = { assignTagDialogState = null },
            title = { Text("Item Added Successfully") },
            text = { Text("Do you want to assign an NFC tag to '$itemName' now?") },
            confirmButton = {
                Button(
                    onClick = {
                        assignTagDialogState = null
                        navController.navigate("scanner/$itemId")
                    }
                ) {
                    Text("Assign Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { assignTagDialogState = null }
                ) {
                    Text("Later")
                }
            }
        )
    }
}
