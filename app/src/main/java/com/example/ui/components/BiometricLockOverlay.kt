package com.example.ui.components

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.*
import com.example.util.BiometricAuthManager

@Composable
fun BiometricLockOverlay(
    onUnlockSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var authError by remember { mutableStateOf<String?>(null) }

    // System Device Credential (PIN, Pattern, Passcode) result launcher
    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            authError = null
            onUnlockSuccess()
        } else {
            authError = "PIN/Passcode verification failed or cancelled. Please try again."
        }
    }

    fun triggerDeviceCredential() {
        if (BiometricAuthManager.isDeviceSecure(context)) {
            val intent = BiometricAuthManager.getDeviceCredentialIntent(
                context = context,
                title = "Unlock Cash Trace",
                description = "Enter your device PIN, pattern, or passcode to access your finances"
            )
            if (intent != null) {
                deviceCredentialLauncher.launch(intent)
            } else {
                authError = "Could not open system credential screen."
            }
        } else {
            authError = "No screen lock PIN or passcode found on this device. Please set a screen lock in Android Settings."
        }
    }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity
        if (activity != null) {
            BiometricAuthManager.promptBiometric(
                activity = activity,
                title = "Unlock Cash Trace",
                subtitle = "Biometric authentication required to view your finances",
                onSuccess = {
                    authError = null
                    onUnlockSuccess()
                },
                onError = { err ->
                    authError = err
                }
            )
        } else {
            triggerDeviceCredential()
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EmeraldDark,
                        NavyDeep,
                        Color(0xFF030712)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("biometric_lock_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            // Glowing Logo Box
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF152036)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_foreground_art),
                    contentDescription = "Cash Trace Logo",
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Cash Trace Protected",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Your financial transactions and monthly budgets are encrypted & secured with biometrics.",
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )

            if (authError != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    color = Color(0x33EF4444),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = authError ?: "",
                        fontSize = 11.5.sp,
                        color = Color(0xFFFCA5A5),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { triggerBiometric() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("unlock_biometric_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Biometrics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = { triggerDeviceCredential() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("unlock_pin_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1))
            ) {
                Icon(
                    imageVector = Icons.Default.Password,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock with Device PIN / Passcode",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
