package com.vaultdex.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vaultdex.app.VaultDexApp
import com.vaultdex.app.data.backup.BackupManager
import com.vaultdex.app.data.cache.ProfileCacheManager
import com.vaultdex.app.data.db.TransactionEntity
import com.vaultdex.app.data.prefs.AuthPrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProfileUiState(
    val cachedProfile: ProfileCacheManager.CachedProfile? = null,
    val transactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = false,
    val backupPath: String? = null,
    val backupError: String? = null,
    val message: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = (application as VaultDexApp).database
    private val transactionDao = db.transactionDao()
    private val authPrefs      = AuthPrefsManager(application)
    private val profileCache   = ProfileCacheManager(application)
    private val backupManager  = BackupManager(application)

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
        loadTransactions()
        seedMockTransactions()
    }

    private fun loadProfile() {
        // VULN-D: Loads from cacheDir — sensitive data persists across sessions
        val profile = profileCache.loadCachedProfile()
        _uiState.value = _uiState.value.copy(cachedProfile = profile, isLoading = false)
    }

    private fun loadTransactions() {
        viewModelScope.launch {
            transactionDao.getAllTransactions().collect { txList ->
                _uiState.value = _uiState.value.copy(transactions = txList)
            }
        }
    }

    private fun seedMockTransactions() {
        viewModelScope.launch {
            val existing = transactionDao.getAllTransactions().first()
            if (existing.isEmpty()) {
                val mockTxs = listOf(
                    TransactionEntity(
                        type = "credit", amount = 3500.00,
                        description = "Salary deposit",
                        recipientAccount = authPrefs.getAccountNumber()
                    ),
                    TransactionEntity(
                        type = "debit", amount = 149.99,
                        description = "Netflix subscription",
                        recipientAccount = "NET-PAY-0034-ACC"
                    ),
                    TransactionEntity(
                        type = "debit", amount = 82.50,
                        description = "Transfer to savings",
                        recipientAccount = "VLT-${(1000000..9999999).random()}-SAV"
                    ),
                    TransactionEntity(
                        type = "credit", amount = 250.00,
                        description = "Freelance payment received",
                        recipientAccount = authPrefs.getAccountNumber()
                    )
                )
                mockTxs.forEach { transactionDao.insertTransaction(it) }
            }
        }
    }

    /**
     * VULN-C: Triggers plaintext JSON backup to internal storage.
     * All credentials, notes, and transactions written unencrypted.
     */
    fun createBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val notes       = (application as VaultDexApp).database.noteDao().getAllNotes().first()
            val transactions = transactionDao.getAllTransactions().first()

            // VULN-C: Backup includes plaintext password and all sensitive data
            val path = backupManager.createBackup(
                username      = authPrefs.getEmail(),
                password      = authPrefs.getPassword(),     // Plaintext password in backup
                sessionToken  = authPrefs.getSessionToken(),
                accountNumber = authPrefs.getAccountNumber(),
                cachedProfile = profileCache.loadCachedProfile(),
                notes         = notes,
                transactions  = transactions
            )

            _uiState.value = _uiState.value.copy(
                isLoading   = false,
                backupPath  = path,
                message     = if (path != null) "Backup created successfully!" else null,
                backupError = if (path == null) "Backup failed" else null
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, backupPath = null, backupError = null)
    }
}
