package com.vaultdex.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultdex.app.viewmodel.AuthViewModel

/**
 * PIN Screen
 * VULN-A2: PIN is AES-CBC encrypted with a hardcoded key and static IV
 * before being saved to SharedPreferences (pin_prefs.xml).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val pinState by authViewModel.pinState.collectAsState()
    var pinInput by remember { mutableStateOf("") }
    var mode     by remember { mutableStateOf(if (pinState.hasPin) "verify" else "set") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(pinState.message) {
        pinState.message?.let {
            snackbarHostState.showSnackbar(it)
            authViewModel.resetPinState()
        }
    }
    LaunchedEffect(pinState.pinSaved) {
        if (pinState.pinSaved) mode = "verify"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Secure PIN", fontWeight = FontWeight.Bold)
                        Text(
                            "VULN-A2: AES with hardcoded key + static IV",
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {

            // Lock icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (mode == "verify") Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text  = if (mode == "set") "Set Your PIN" else "Enter Your PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text  = if (mode == "set")
                    "Choose a 6-digit PIN to protect your vault"
                else
                    "Enter your 6-digit PIN to verify",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            // PIN dots display
            PinDotsDisplay(pinLength = pinInput.length, maxLength = 6)

            // Keypad
            PinKeypad(
                onDigit  = { if (pinInput.length < 6) pinInput += it },
                onDelete = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) },
                onSubmit = {
                    if (pinInput.length == 6) {
                        if (mode == "set") authViewModel.savePin(pinInput)
                        else authViewModel.verifyPin(pinInput)
                        pinInput = ""
                    }
                }
            )

            // Toggle mode
            TextButton(
                onClick = {
                    mode = if (mode == "set") "verify" else "set"
                    pinInput = ""
                    authViewModel.resetPinState()
                }
            ) {
                Text(
                    text  = if (mode == "set") "Already have a PIN? Verify it" else "Set a new PIN",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Vuln info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text  = "⚠️  Vuln Detail: VULN-A2",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Key: \"V@u1tD3xS3cr3tKey\" (hardcoded)\nIV: \"VaultDexIV123456\" (static)\nFile: shared_prefs/pin_prefs.xml",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
        }
    }
}

@Composable
fun PinDotsDisplay(pinLength: Int, maxLength: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(maxLength) { i ->
            val filled = i < pinLength
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled)
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        else
                            Modifier
                                .background(Color.Transparent)
                                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
            )
        }
    }
}

@Composable
fun PinKeypad(onDigit: (String) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        when {
                            key.isEmpty() -> Spacer(Modifier.size(72.dp))
                            key == "⌫" -> OutlinedIconButton(
                                onClick  = onDelete,
                                modifier = Modifier.size(72.dp),
                                shape    = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Default.Backspace, "Delete", modifier = Modifier.size(22.dp))
                            }
                            else -> FilledTonalButton(
                                onClick  = { onDigit(key) },
                                modifier = Modifier.size(72.dp),
                                shape    = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick  = onSubmit,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
