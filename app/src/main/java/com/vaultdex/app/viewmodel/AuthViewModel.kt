package com.vaultdex.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaultdex.app.data.backup.BackupManager
import com.vaultdex.app.data.cache.LoggerCacheManager
import com.vaultdex.app.data.cache.ProfileCacheManager
import com.vaultdex.app.data.prefs.ApiKeyManager
import com.vaultdex.app.data.prefs.AuthPrefsManager
import com.vaultdex.app.data.prefs.BiometricPrefsManager
import com.vaultdex.app.data.prefs.OAuthTokenManager
import com.vaultdex.app.data.prefs.PinCryptoManager
import com.vaultdex.app.data.prefs.WeakHashManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val errorMessage: String? = null,
    val loginSuccess: Boolean = false
)

data class PinUiState(
    val hasPin: Boolean = false,
    val pinSaved: Boolean = false,
    val pinVerified: Boolean = false,
    val pinError: Boolean = false,
    val message: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authPrefs      = AuthPrefsManager(application)
    private val pinCrypto      = PinCryptoManager(application)
    private val profileCache   = ProfileCacheManager(application)
    private val backupManager  = BackupManager(application)

    // New vulnerable managers
    private val apiKeyManager         = ApiKeyManager(application)
    private val oauthTokenManager     = OAuthTokenManager(application)
    private val weakHashManager       = WeakHashManager(application)
    private val biometricPrefsManager = BiometricPrefsManager(application)
    private val loggerCacheManager    = LoggerCacheManager(application)

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _pinState = MutableStateFlow(PinUiState())
    val pinState: StateFlow<PinUiState> = _pinState.asStateFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        val isLoggedIn = authPrefs.isLoggedIn()
        _authState.value = AuthUiState(
            isLoggedIn  = isLoggedIn,
            displayName = authPrefs.getDisplayName(),
            email       = authPrefs.getEmail()
        )
        _pinState.value = PinUiState(hasPin = pinCrypto.hasPin())
    }

    /**
     * Simulates a login — VULN-A1: saves credentials to plaintext SharedPreferences.
     */
    fun login(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, errorMessage = null)

            if (email.isBlank() || password.isBlank()) {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    errorMessage = "Email and password are required"
                )
                return@launch
            }

            // VULN-A1: Saves plaintext credentials to SharedPreferences
            authPrefs.saveLoginCredentials(
                username    = email,
                password    = password,
                displayName = displayName.ifBlank { email.substringBefore("@") }
            )

            // Seed new vulnerabilities
            apiKeyManager.seedApiKeys()
            oauthTokenManager.seedTokens(email)
            weakHashManager.storePasswordHash(password)
            biometricPrefsManager.seedBiometricData()
            
            // Seed vulnerable cache
            loggerCacheManager.logApiResponse(
                endpoint = "https://api.vaultdex.com/v1/login",
                requestBody = "{\"username\":\"$email\",\"password\":\"$password\"}",
                responseBody = "{\"status\":\"success\",\"token\":\"eyJhbGciOiJIUzI1Ni...\"}"
            )
            loggerCacheManager.cacheCertificatePinningKey()

            // VULN-D: Fetch and cache profile — sensitive data stored in cacheDir
            profileCache.fetchAndCacheMockProfile(
                userId        = email.hashCode().toString(),
                displayName   = displayName.ifBlank { email.substringBefore("@") },
                email         = email,
                accountNumber = authPrefs.getAccountNumber()
            )

            _authState.value = AuthUiState(
                isLoggedIn   = true,
                displayName  = authPrefs.getDisplayName(),
                email        = email,
                loginSuccess = true
            )
        }
    }

    /**
     * VULN-D: Logout deliberately does NOT clear the profile cache.
     * The cached profile JSON remains in cacheDir after logout.
     */
    fun logout() {
        authPrefs.logout()
        // VULN-D: profileCache.clearCache() is intentionally NOT called here
        // The cache file at /data/.../cache/profile_cache.json persists
        _authState.value = AuthUiState(isLoggedIn = false)
    }

    // PIN management — delegates to PinCryptoManager (VULN-A2)
    fun savePin(pin: String) {
        pinCrypto.savePin(pin)  // VULN-A2: encrypted with hardcoded key
        _pinState.value = PinUiState(hasPin = true, pinSaved = true, message = "PIN saved successfully")
    }

    fun verifyPin(pin: String) {
        val isCorrect = pinCrypto.verifyPin(pin)
        _pinState.value = _pinState.value.copy(
            pinVerified = isCorrect,
            pinError    = !isCorrect,
            message     = if (isCorrect) "PIN verified!" else "Incorrect PIN"
        )
    }

    fun resetPinState() {
        _pinState.value = _pinState.value.copy(
            pinSaved = false, pinVerified = false, pinError = false, message = null
        )
    }

    fun getAccountNumber(): String = authPrefs.getAccountNumber()
    fun getStoredPassword(): String = authPrefs.getPassword()  // Exposes plaintext password for demo
}
