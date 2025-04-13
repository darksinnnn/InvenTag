package com.example.inventag.screens.inventory

//import com.example.inventag.screens.inventory.InventoryItemCard
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
//import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
//import com.example.inventag.models.InventoryItem
import com.example.inventag.ui.components.BottomNavBar
//import com.example.inventag.ui.theme.ExpiredRed
//import com.example.inventag.ui.theme.LowStockOrange
//import com.example.inventag.ui.theme.ValidGreen
import com.example.inventag.viewmodels.InventoryViewModel
//import java.text.SimpleDateFormat
//import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    navController: NavController,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Handle NFC scanning for item association
    LaunchedEffect(isScanning) {
        if (!isScanning) {
            viewModel.stopNfcScan()
        }
    }

    // Collect other state flows
//    val detectedTagId by viewModel.detectedTagId.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()

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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (inventoryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                    Text(
                        text = "No inventory items yet",
                        style = MaterialTheme.typography.titleLarge
                    )
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

        // Show NFC scanning overlay if scanning
        if (isScanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Scanning for NFC Tag",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hold an NFC tag near your device to associate it with this item",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.stopNfcScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditInventoryItemDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, quantity, expiryDate, category ->
                viewModel.addItem(name, quantity, expiryDate, category)
                showAddDialog = false
            },
            onScanNfcTag = {
                viewModel.startNfcScanForNewItem()
                showAddDialog = false
            }
        )
    }

    selectedItem?.let { item ->
        AddEditInventoryItemDialog(
            item = item,
            onDismiss = { viewModel.clearSelectedItem() },
            onSave = { name, quantity, expiryDate, category ->
                viewModel.updateItem(item.id, name, quantity, expiryDate, category)
                viewModel.clearSelectedItem()
            },
            onScanNfcTag = {
                viewModel.startNfcScanForExistingItem(item.id)
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

    // Show dialog for new NFC tag detection
//    detectedTagId?.let { tagId ->
//        AlertDialog(
//            onDismissRequest = { viewModel.clearDetectedTag() },
//            title = { Text("New NFC Tag Detected") },
//            text = { Text("Would you like to associate this NFC tag (ID: $tagId) with a product?") },
//            confirmButton = {
//                Button(
//                    onClick = {
//                        viewModel.associateTagWithPendingItem()
//                        viewModel.clearDetectedTag()
//                    }
//                ) {
//                    Text("Associate")
//                }
//            },
//            dismissButton = {
//                TextButton(
//                    onClick = { viewModel.clearDetectedTag() }
//                ) {
//                    Text("Ignore")
//                }
//            }
//        )
//    }
}
