package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.GoogleSignInResult
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onContactClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by rememberSaveable { mutableIntStateOf(0) }
    var cooldownSeconds by rememberSaveable { mutableIntStateOf(0) }
    var activeTimeoutSeconds by remember { mutableIntStateOf(0) }
    val signInInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds -= 1
            if (cooldownSeconds == 0) {
                failedAttempts = 0
                statusMessage = null
            }
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            activeTimeoutSeconds = (AuthManager.LOGIN_TIMEOUT_MS / 1000L).toInt()
            while (activeTimeoutSeconds > 0) {
                delay(1000L)
                activeTimeoutSeconds -= 1
            }
        } else {
            activeTimeoutSeconds = 0
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 24.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(19.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.trolley_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp)),
                )

                Text(
                    text = stringResource(R.string.auth_eyebrow),
                    style = MaterialTheme.typography.labelSmall,
                    color = TrolleyTheme.colors.orange,
                )

                Text(
                    text = stringResource(R.string.auth_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = stringResource(R.string.auth_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrolleyTheme.colors.mutedInk,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AuthFeatureRow(
                            icon = Icons.Rounded.Storefront,
                            text = "Sign in or sign up with Google to save private business drafts",
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.VerifiedUser,
                            text = "Listing approval is reviewed by Trolley administrators",
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.Timer,
                            text = "45s login timeout limit · 10-minute verification window",
                        )
                        AuthFeatureRow(
                            icon = Icons.Rounded.Lock,
                            text = "Your account and draft profile remain private to you",
                        )
                    }
                }

                val isLockedOut = cooldownSeconds > 0
                Button(
                    onClick = {
                        if (isLoading || isLockedOut) return@Button
                        isLoading = true
                        statusMessage = null
                        scope.launch {
                            when (val result = AuthManager.signInInteractive(context)) {
                                is GoogleSignInResult.Success -> {
                                    isLoading = false
                                    failedAttempts = 0
                                    cooldownSeconds = 0
                                    statusMessage = if (result.isNewUser) {
                                        "Welcome to Trolley, ${result.displayName}! Google account registered."
                                    } else {
                                        "Signed in as ${result.displayName}."
                                    }
                                    onAuthSuccess()
                                }
                                is GoogleSignInResult.Cancelled -> {
                                    isLoading = false
                                    statusMessage = "Sign-in was cancelled. Tap below whenever you're ready to continue."
                                }
                                is GoogleSignInResult.Timeout -> {
                                    isLoading = false
                                    failedAttempts += 1
                                    if (failedAttempts >= AuthManager.MAX_FAILED_ATTEMPTS) {
                                        cooldownSeconds = AuthManager.COOLDOWN_SECONDS
                                        statusMessage = "Too many timed-out attempts. Sign-in paused for ${AuthManager.COOLDOWN_SECONDS}s."
                                    } else {
                                        statusMessage = "Login timed out after 45 seconds. Please check your connection and try again."
                                    }
                                }
                                is GoogleSignInResult.Error -> {
                                    isLoading = false
                                    failedAttempts += 1
                                    if (failedAttempts >= AuthManager.MAX_FAILED_ATTEMPTS) {
                                        cooldownSeconds = AuthManager.COOLDOWN_SECONDS
                                        statusMessage = "Too many failed sign-in attempts. Please wait ${AuthManager.COOLDOWN_SECONDS}s."
                                    } else {
                                        statusMessage = result.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isLoading && !isLockedOut,
                    interactionSource = signInInteraction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .trolleySpringPress(signInInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("google_signin_button"),
                ) {
                    AnimatedContent(
                        targetState = when {
                            isLoading -> "loading"
                            isLockedOut -> "locked"
                            else -> "ready"
                        },
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "auth_button_content",
                    ) { state ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            when (state) {
                                "loading" -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (activeTimeoutSeconds > 0) {
                                            "Signing in (${activeTimeoutSeconds}s timeout)…"
                                        } else {
                                            "Signing in…"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                "locked" -> {
                                    Text(
                                        text = "Try again in ${cooldownSeconds}s",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "G",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 16.sp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.sign_in_google),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = statusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    ) {
                        Text(
                            text = statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "By continuing, you agree to our",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                        TextButton(
                            onClick = onOpenTerms,
                            modifier = Modifier.testTag("auth_terms_button"),
                        ) {
                            Text("Terms", color = TrolleyTheme.colors.orange, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "and",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                        TextButton(
                            onClick = onOpenPrivacy,
                            modifier = Modifier.testTag("auth_privacy_button"),
                        ) {
                            Text("Privacy Policy", color = TrolleyTheme.colors.orange, fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = onContactClick,
                        modifier = Modifier.testTag("auth_support_contact_button"),
                    ) {
                        Text(
                            text = "Need help? ${stringResource(R.string.support_email)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TrolleyTheme.colors.limeAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
