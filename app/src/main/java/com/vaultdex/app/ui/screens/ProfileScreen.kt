package com.vaultdex.app.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultdex.app.data.db.TransactionEntity
import com.vaultdex.app.viewmodel.AuthViewModel
import com.vaultdex.app.viewmodel.ProfileViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile Screen
 * VULN-C: "Export Backup" triggers plaintext JSON write to internal storage.
 * VULN-D: Profile data loaded from cacheDir (survives logout).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val authState    by authViewModel.authState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val profile      = profileState.cachedProfile

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(profileState.message) {
        profileState.message?.let {
            snackbarHostState.showSnackbar(it)
            profileViewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Profile & Backup", fontWeight = FontWeight.Bold)
                        Text(
                            "VULN-C/D: Cache & Plaintext Backup",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Profile Header Card
            ProfileHeaderCard(
                displayName   = authState.displayName,
                email         = authState.email,
                accountNumber = authState.email.hashCode().toString()
            )

            // Financial details from cache (VULN-D)
            if (profile != null) {
                FinancialDetailsCard(profile)
            }

            // Transaction History
            Text(
                text  = "Transaction History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text  = "VULN-B: Stored in unencrypted vault_data.db",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )

            profileState.transactions.forEach { tx ->
                TransactionItem(tx)
            }

            Spacer(Modifier.height(8.dp))

            // Backup section
            BackupSection(
                isLoading  = profileState.isLoading,
                backupPath = profileState.backupPath,
                onBackup   = { profileViewModel.createBackup() }
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProfileHeaderCard(displayName: String, email: String, accountNumber: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF6B3FA0), Color(0xFF0097B2))),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Text(
                        text     = "Active",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FinancialDetailsCard(profile: com.vaultdex.app.data.cache.ProfileCacheManager.CachedProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Financial Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text  = "VULN-D: Loaded from cache/profile_cache.json (not cleared on logout)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            HorizontalDivider()
            DetailRow("Balance",       "${"%.2f".format(profile.balance)} USD")
            DetailRow("Card",          profile.cardNumber)
            DetailRow("Expiry",        profile.cardExpiry)
            DetailRow("Credit Score",  profile.creditScore.toString())
            DetailRow("Account #",     profile.accountNumber)
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TransactionItem(tx: TransactionEntity) {
    val isCredit = tx.type == "credit"
    val color    = if (isCredit) Color(0xFF4CAF82) else MaterialTheme.colorScheme.error
    val icon     = if (isCredit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(tx.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(tx.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            Text(
                text  = "${if (isCredit) "+" else "-"}${"%.2f".format(tx.amount)}",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun BackupSection(isLoading: Boolean, backupPath: String?, onBackup: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Backup, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Text("Account Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text  = "VULN-C: Creates a plaintext JSON backup of all credentials, notes, and transactions in internal storage (files/backup/).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            if (backupPath != null) {
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF82).copy(alpha = 0.12f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF82), modifier = Modifier.size(18.dp))
                        Text(
                            text  = "Saved: ${backupPath.substringAfterLast("/")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF82)
                        )
                    }
                }
            }
            Button(
                onClick  = onBackup,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export Plaintext Backup", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
