package com.example.inventag.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.inventag.navigation.Routes

data class BottomNavItem(
    val name: String,
    val route: String,
    val icon: ImageVector
)

// Update BottomNavBar to include Cart
@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        BottomNavItem(
            name = "Home",
            route = Routes.Home.route,
            icon = Icons.Default.Home
        ),
        BottomNavItem(
            name = "Inventory",
            route = Routes.Inventory.route,
            icon = Icons.Default.Inventory2
        ),
        BottomNavItem(
            name = "Scan",
            route = Routes.Scanner.route,
            icon = Icons.Default.QrCodeScanner
        ),
        BottomNavItem(
            name = "Cart",
            route = Routes.Cart.route,
            icon = Icons.Default.ShoppingCart
        ),
        BottomNavItem(
            name = "Profile",
            route = Routes.Profile.route,
            icon = Icons.Default.Person
        ),
        BottomNavItem(  // 👈 Add Settings here
             name = "Settings",
             route = Routes.Settings.route,
             icon = Icons.Default.Settings
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.name) },
                label = { Text(item.name) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        navController.graph.startDestinationRoute?.let { route ->
                            popUpTo(route) {
                                saveState = true
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

