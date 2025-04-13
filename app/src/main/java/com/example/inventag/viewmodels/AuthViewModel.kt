package com.example.inventag.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventag.data.repositories.AuthRepository
import com.example.inventag.data.repositories.UserPreferencesRepository
import com.example.inventag.models.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        checkAuthState()
        loadThemePreference()
    }

    private fun checkAuthState() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.getCurrentUser()?.let { firebaseUser ->
                _currentUser.value = User(
                    id = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString()
                )
                _isLoggedIn.value = true
            } ?: run {
                _isLoggedIn.value = false
                _currentUser.value = null
            }
            _isLoading.value = false
        }
    }

    private fun loadThemePreference() {
        viewModelScope.launch {
            userPreferencesRepository.isDarkMode.collect { isDark ->
                _isDarkMode.value = isDark
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                authRepository.login(email, password)
                checkAuthState()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false  // Ensure loading state resets
            }
        }
    }

    fun signup(name: String, email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                authRepository.signup(name, email, password)
                checkAuthState() // Reuse function instead of manual assignment
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false // Ensure loading state resets
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.logout()
                _isLoggedIn.value = false
                _currentUser.value = null
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(name: String, photoUrl: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                authRepository.updateProfile(name, photoUrl)
                checkAuthState() // Use this instead of manual update
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false // Ensure loading state resets
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val newMode = !(isDarkMode.value ?: false)
            userPreferencesRepository.setDarkMode(newMode)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
