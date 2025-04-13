package com.example.inventag.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.inventag.navigation.Routes
import com.example.inventag.ui.components.BottomNavBar

data class HomeMenuItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController
) {
    val menuItems = listOf(
        HomeMenuItem(
            title = "Inventory",
            icon = Icons.Default.Inventory2,
            route = Routes.Inventory.route,
            description = "Manage your inventory items"
        ),
        HomeMenuItem(
            title = "Scan NFC",
            icon = Icons.Default.QrCodeScanner,
            route = Routes.Scanner.route,
            description = "Scan NFC tags to update inventory"
        ),
        HomeMenuItem(
            title = "Past Orders",
            icon = Icons.Default.History,
            route = Routes.Orders.route,
            description = "View your scan history"
        ),
        HomeMenuItem(
            title = "Reports",
            icon = Icons.Default.BarChart,
            route = Routes.Reports.route,
            description = "View inventory insights"
        ),
        HomeMenuItem(
            title = "Alerts",
            icon = Icons.Default.Notifications,
            route = Routes.Alerts.route,
            description = "Check important notifications"
        ),
        HomeMenuItem(
            title = "Profile",
            icon = Icons.Default.Person,
            route = Routes.Profile.route,
            description = "Manage your account"
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("InvenTag") }
            )
        },
        bottomBar = {
            BottomNavBar(navController = navController)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to InvenTag",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(menuItems) { item ->
                    HomeMenuCard(
                        menuItem = item,
                        onClick = { navController.navigate(item.route) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuCard(
    menuItem: HomeMenuItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = menuItem.icon,
                contentDescription = menuItem.title,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = menuItem.title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = menuItem.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

