package com.example.inventag.navigation

sealed class Routes(val route: String) {
    object Login : Routes("login")
    object Signup : Routes("signup")
    object Home : Routes("home")
    object Profile : Routes("profile")
    object Inventory : Routes("inventory")
    object Scanner : Routes("scanner")
    object Orders : Routes("orders")
    object Reports : Routes("reports")
    object Alerts : Routes("alerts")
    object Cart : Routes("cart")
    object Settings : Routes("settings")
}

