package com.example.inventag.navigation

import com.example.inventag.screens.cart.CartScreen
import com.example.inventag.screens.settings.SettingsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.inventag.screens.alerts.AlertsScreen
import com.example.inventag.screens.auth.LoginScreen
import com.example.inventag.screens.auth.SignupScreen
import com.example.inventag.screens.home.HomeScreen
import com.example.inventag.screens.inventory.InventoryScreen
import com.example.inventag.screens.orders.OrdersScreen
import com.example.inventag.screens.profile.ProfileScreen
import com.example.inventag.screens.reports.ReportsScreen
import com.example.inventag.screens.scanner.ScannerScreen
import com.example.inventag.viewmodels.AuthViewModel

// Update AppNavigation to include new routes
@Composable
fun AppNavigation(
    navController: NavHostController,
    authViewModel: AuthViewModel
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Routes.Home.route else Routes.Login.route
    ) {
        // Auth screens
        composable(Routes.Login.route) {
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(Routes.Signup.route) {
            SignupScreen(navController = navController, authViewModel = authViewModel)
        }

        // Main app screens
        composable(Routes.Home.route) {
            HomeScreen(navController = navController)
        }
        composable(Routes.Profile.route) {
            ProfileScreen(navController = navController, authViewModel = authViewModel)
        }
        composable(Routes.Inventory.route) {
            InventoryScreen(navController = navController)
        }
        composable(Routes.Scanner.route) {
            ScannerScreen(navController = navController)
        }
        composable(Routes.Orders.route) {
            OrdersScreen(navController = navController)
        }
        composable(Routes.Reports.route) {
            ReportsScreen(navController = navController)
        }
        composable(Routes.Alerts.route) {
            AlertsScreen(navController = navController)
        }
        composable(Routes.Cart.route) {
            CartScreen(navController = navController)
        }
        composable(Routes.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}

