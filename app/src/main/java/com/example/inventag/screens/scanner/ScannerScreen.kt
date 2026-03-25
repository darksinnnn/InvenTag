package com.example.inventag.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.inventag.models.InventoryItem
import com.example.inventag.ui.components.BottomNavBar
import com.example.inventag.ui.theme.ExpiredRed
import com.example.inventag.ui.theme.LowStockOrange
import com.example.inventag.ui.theme.ValidGreen
import com.example.inventag.viewmodels.ScannerUiState
import com.example.inventag.viewmodels.ScannerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    navController: NavController,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    // **FIXED**: Collect the single UI state object
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("NFC Scanner") }
            )
        },
        bottomBar = {
            BottomNavBar(navController = navController)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // **FIXED**: Use a 'when' expression to handle the different UI states
            when (val state = uiState) {
                is ScannerUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is ScannerUiState.Success -> {
                    ScannedItemDetails(
                        item = state.item,
                        statusMessage = state.statusMessage,
                        onDismiss = { viewModel.clearUiState() }
                    )
                }
                // All other states (Idle, Scanning, AssignTag, Error) are handled by the main content view
                else -> {
                    ScannerContent(
                        state = state,
                        onStartScan = { viewModel.startScanning() },
                        onStopScan = { viewModel.stopScanning() },
                        onAssignTag = { itemId, tagId -> viewModel.assignTagToItem(itemId, tagId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerContent(
    state: ScannerUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onAssignTag: (String, String) -> Unit
) {
    var selectedItemId by remember { mutableStateOf<String?>(null) }
    val isScanning = state is ScannerUiState.Scanning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- Top section: Icon and Status Text ---
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(80.dp), color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // **FIXED**: Display message based on the current state
        val messageToDisplay = when (state) {
            is ScannerUiState.Idle -> "Ready to Scan"
            is ScannerUiState.Scanning -> "Scanning... Place tag near device"
            is ScannerUiState.AssignTag -> "New tag detected. Please assign it to an item."
            is ScannerUiState.Error -> state.message
            else -> "" // Other states handled elsewhere
        }
        Text(text = messageToDisplay, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))

        // --- Middle section: Scan Button ---
        // Show the button only if we are not in the 'AssignTag' state
        if (state !is ScannerUiState.AssignTag) {
            Button(
                onClick = if (isScanning) onStopScan else onStartScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            ) {
                Icon(if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isScanning) "Stop Scanning" else "Start Scanning")
            }
        }

        // --- Bottom section: Item Assignment List ---
        // Only shows when the state is 'AssignTag'
        if (state is ScannerUiState.AssignTag) {
            val availableItems = state.availableItems
            val tagId = state.tagId

            Spacer(modifier = Modifier.height(16.dp))

            if (availableItems.isEmpty()) {
                Text("No unassigned items available.")
            } else {
                Column {
                    // **FIXED**: Use a standard 'for' loop for Composable
                    for (item in availableItems) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = selectedItemId == item.id,
                                onClick = { selectedItemId = item.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                                Text(text = "Qty: ${item.quantity}, Category: ${item.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            selectedItemId?.let {
                                onAssignTag(it, tagId)
                            }
                        },
                        enabled = selectedItemId != null, // Button is disabled until an item is selected
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Attach Tag to Item")
                    }
                }
            }
        }
    }
}

@Composable
fun ScannedItemDetails(
    item: InventoryItem,
    statusMessage: String, // Receive the specific status message
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val isExpired = item.isExpired()
    val isLowStock = item.isLowStock()

    val statusColor = when {
        isExpired -> ExpiredRed
        isLowStock -> LowStockOrange
        else -> ValidGreen
    }
    val statusText = when {
        isExpired -> "Expired"
        isLowStock -> "Low Stock"
        else -> "In Stock"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            // **FIXED**: Use the dynamic status message from the state
            text = statusMessage,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Category: ${item.category}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Quantity", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = item.quantity.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                    item.expiryDate?.let { expiry ->
                        val expiryDate = dateFormat.format(expiry.toDate())
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Expires On", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = expiryDate, style = MaterialTheme.typography.titleLarge)
                        }
                    } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Expiry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "N/A", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Scan Another Item")
        }
    }
}