package com.example.inventag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.inventag.navigation.AppNavigation
import com.example.inventag.ui.theme.InvenTagTheme
import com.example.inventag.viewmodels.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val authViewModel: AuthViewModel = viewModel()
            val isDarkMode by authViewModel.isDarkMode.collectAsState()

            InvenTagTheme(darkTheme = isDarkMode ?: isSystemInDarkTheme()) {
                val navController = rememberNavController()
                AppNavigation(navController = navController, authViewModel = authViewModel)
            }
        }
    }
}

