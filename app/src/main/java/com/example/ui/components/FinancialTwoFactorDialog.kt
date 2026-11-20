package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.catalog.BillingCatalog
import com.example.data.security.TwoFactorAuthenticator
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.TwoFactorChallengeUiState
import kotlinx.coroutines.delay

@Composable
fun FinancialTwoFactorDialog(
    state: TwoFactorChallengeUiState,
    onRequestFirebaseReauth: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var otpCode by rememberSaveable { mutableStateOf("") }
    var copiedSecret by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(TwoFactorAuthenticator.secondsRemainingInStep()) }

    LaunchedEffect(Unit) {
        while (true) {
            secondsLeft = TwoFactorAuthenticator.secondsRemainingInStep()
            delay(1000L)
        }
    }

    val isStep1Verified = state.remainingReauthMillis > 0L && state.firebaseAuthTimeSeconds != null
    val isSetupEnrollment = state.enrollmentSecretBase32 != null
    val cooldownSeconds = state.localTwoFactorStatus.remainingCooldownSeconds()

    Dialog(
        onDismissRequest = { if (!state.isSubmitting && !state.isReauthenticating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .testTag("financial_twofa_dialog"),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TrolleyTheme.colors.lime,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.limeDeep,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.twofa_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.orange,
                        )
                        Text(
                            text = if (state.isSetupOnlyMode) {
                                "Configure Provider 2FA Authenticator"
                            } else {
                                stringResource(R.string.twofa_title)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.twofa_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                )

                // Pending Checkout Summary pill
                val pending = state.pendingCheckout
                if (pending != null) {
                    val quote = BillingCatalog.computeQuote(pending.tier, pending.cycle)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Authorizing ${pending.tier.title} (${pending.cycle.label})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${quote.formattedPrice} USD via ${pending.maskedInstrument}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.orange,
                            )
                        }
                    }
                }

                HorizontalDivider(color = TrolleyTheme.colors.hairline)

                // FACTOR 1: Firebase Auth Re-authentication
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isStep1Verified) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.hairline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.twofa_step1_title),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = if (isStep1Verified) Icons.Rounded.CheckCircle else Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = if (isStep1Verified) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Text(
                            text = if (isStep1Verified) {
                                val mins = ((state.remainingReauthMillis + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
                                "Firebase Auth verified (${state.accountEmail}) · Valid for ~${mins}m"
                            } else {
                                "Re-verify your Google / Firebase Auth session before authorizing financial changes."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )

                        OutlinedButton(
                            onClick = onRequestFirebaseReauth,
                            enabled = !state.isReauthenticating && !state.isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("twofa_firebase_reauth_button"),
                        ) {
                            if (state.isReauthenticating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Contacting Firebase Auth…")
                            } else {
                                Text(
                                    if (isStep1Verified) {
                                        "Re-verify Firebase Auth Again"
                                    } else {
                                        stringResource(R.string.twofa_verify_google_btn)
                                    }
                                )
                            }
                        }
                    }
                }

                // FACTOR 2: 6-digit TOTP Authenticator Code
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.twofa_step2_title),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = TrolleyTheme.colors.lime,
                            ) {
                                Text(
                                    text = "${secondsLeft}s",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TrolleyTheme.colors.limeDeep,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        if (isSetupEnrollment && state.enrollmentSecretBase32 != null) {
                            Text(
                                text = "Add this key to Google Authenticator, Microsoft Authenticator, or Authy, then enter the live 6-digit code below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.mutedInk,
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.background,
                                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "SETUP SECRET (BASE32 · HMAC-SHA1)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TrolleyTheme.colors.orange,
                                    )
                                    Text(
                                        text = state.enrollmentSecretBase32.chunked(4).joinToString(" "),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.testTag("twofa_setup_secret_text"),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Trolley 2FA Key", state.enrollmentSecretBase32)
                                        )
                                        copiedSecret = true
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("twofa_copy_secret_button"),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (copiedSecret) "Copied!" else stringResource(R.string.twofa_copy_key_btn))
                                }
                                if (!state.otpAuthUri.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.otpAuthUri))
                                            runCatching { context.startActivity(intent) }
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("twofa_open_authenticator_button"),
                                    ) {
                                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Authenticator")
                                    }
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Key,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.limeAccent,
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = "Keystore-sealed 2FA active · Fingerprint ${state.localTwoFactorStatus.secretFingerprint?.take(12).orEmpty()}…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }

                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { next ->
                                val digits = next.filter { it.isDigit() }
                                if (digits.length <= 6) otpCode = digits
                            },
                            enabled = !state.isSubmitting && cooldownSeconds == 0,
                            label = { Text("6-digit Authenticator code *") },
                            placeholder = { Text("000000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TrolleyTheme.colors.limeAccent,
                                unfocusedBorderColor = TrolleyTheme.colors.hairline,
                                focusedContainerColor = MaterialTheme.colorScheme.background,
                                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("twofa_otp_input"),
                        )
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("twofa_error_text"),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isSubmitting && !state.isReauthenticating,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("twofa_cancel_button"),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSubmitCode(otpCode) },
                        enabled = !state.isSubmitting &&
                            !state.isReauthenticating &&
                            isStep1Verified &&
                            otpCode.length == 6 &&
                            cooldownSeconds == 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("twofa_authorize_button"),
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying…")
                        } else {
                            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (state.isSetupOnlyMode) {
                                    "Activate 2FA"
                                } else if (isSetupEnrollment) {
                                    stringResource(R.string.twofa_enroll_authorize_btn)
                                } else {
                                    stringResource(R.string.twofa_authorize_pay_btn)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
