package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.firebase.AuthManager
import com.example.data.firebase.GoogleSignInResult
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.AccountProviderViewModel
import com.example.ui.viewmodel.MutationNotice
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.min

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserProfileScreen(
    firebaseUser: FirebaseUser,
    viewModel: AccountProviderViewModel,
    onSignedOut: () -> Unit,
    onContactClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by viewModel.userAccountState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val profileRevision by AuthManager.profileRevision.collectAsStateWithLifecycle()

    val account = (accountState as? UiState.Success)?.data
    val activeAuthName = remember(firebaseUser.uid, profileRevision, account?.fullName) {
        firebaseUser.displayName?.ifBlank { null }
            ?: account?.fullName?.ifBlank { null }
            ?: "Trolley Member"
    }
    val activeAuthPhotoUrl = remember(firebaseUser.uid, profileRevision, account?.photoUrl) {
        firebaseUser.photoUrl?.toString()?.takeIf { it.startsWith("https://") }
            ?: account?.photoUrl?.takeIf { it.startsWith("https://") }
    }

    var displayNameInput by rememberSaveable(firebaseUser.uid) {
        mutableStateOf(activeAuthName)
    }
    var photoUrlInput by rememberSaveable(firebaseUser.uid) {
        mutableStateOf(activeAuthPhotoUrl.orEmpty())
    }
    var stagedPhotoBase64 by rememberSaveable(firebaseUser.uid) {
        mutableStateOf<String?>(null)
    }
    var clearedPhoto by rememberSaveable(firebaseUser.uid) {
        mutableStateOf(false)
    }
    var localError by remember { mutableStateOf<String?>(null) }
    var copiedUidNotice by remember { mutableStateOf(false) }

    var remainingFreshMs by remember { mutableStateOf(AuthManager.remainingFreshVerificationMillis()) }
    var isRefreshingSession by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            remainingFreshMs = AuthManager.remainingFreshVerificationMillis()
            delay(15_000L)
        }
    }

    // Sync initial stored fields once loaded if the user hasn't modified them yet
    LaunchedEffect(account?.fullName, account?.photoUrl, account?.photoBase64) {
        if (displayNameInput == "Trolley Member" && !account?.fullName.isNullOrBlank()) {
            displayNameInput = account!!.fullName
        }
        if (photoUrlInput.isBlank() && !account?.photoUrl.isNullOrBlank()) {
            photoUrlInput = account!!.photoUrl.orEmpty()
        }
    }

    val effectivePhotoBase64 = when {
        clearedPhoto -> null
        stagedPhotoBase64 != null -> stagedPhotoBase64
        else -> account?.photoBase64
    }
    val effectivePhotoUrl = when {
        clearedPhoto -> null
        photoUrlInput.trim().startsWith("https://") -> photoUrlInput.trim()
        else -> activeAuthPhotoUrl
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            localError = null
            scope.launch {
                val encoded = encodeAvatarFromUri(context, uri)
                if (encoded != null) {
                    stagedPhotoBase64 = encoded
                    clearedPhoto = false
                } else {
                    localError = "Unable to read the selected image. Please try another photo."
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TrolleyTheme.colors.limeAccent,
        unfocusedBorderColor = TrolleyTheme.colors.hairline,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 580.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Hero Identity Header Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EyebrowLabel(text = stringResource(R.string.profile_eyebrow))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val badgeInteraction = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier.size(92.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ProfileAvatarImage(
                                displayName = displayNameInput.ifBlank { activeAuthName },
                                photoBase64 = effectivePhotoBase64,
                                photoUrl = effectivePhotoUrl,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .border(2.5.dp, TrolleyTheme.colors.lime, CircleShape)
                                    .testTag("profile_avatar_image"),
                            )
                            Surface(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                interactionSource = badgeInteraction,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(34.dp)
                                    .trolleySpringPress(badgeInteraction, pressedScale = 0.9f)
                                    .testTag("profile_avatar_camera_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "Change profile picture",
                                    modifier = Modifier.padding(7.dp),
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = activeAuthName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("profile_header_display_name"),
                            )
                            Text(
                                text = firebaseUser.email ?: "Google account",
                                style = MaterialTheme.typography.bodySmall,
                                color = TrolleyTheme.colors.mutedInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("profile_header_email"),
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val providerLabel = firebaseUser.providerData
                            .firstOrNull { it.providerId != "firebase" }
                            ?.providerId
                            ?.replace(".com", "")
                            ?.replaceFirstChar { it.uppercase() }
                            ?: "Google"
                        StatusPill(
                            icon = Icons.Rounded.Shield,
                            text = "$providerLabel session",
                            highlighted = true,
                        )
                        if (firebaseUser.isEmailVerified) {
                            StatusPill(
                                icon = Icons.Rounded.CheckCircle,
                                text = "Email verified",
                                highlighted = true,
                            )
                        }
                        StatusPill(
                            icon = Icons.Rounded.VerifiedUser,
                            text = if (account?.ageVerified == true) "13+ confirmed" else "Age check pending",
                            highlighted = account?.ageVerified == true,
                        )
                        val remainingMins = ceil(remainingFreshMs / 60_000.0).toInt()
                        StatusPill(
                            icon = Icons.Rounded.Timer,
                            text = if (remainingFreshMs > 0L) "${remainingMins}m fresh window" else "Session >10m",
                            highlighted = remainingFreshMs > 0L,
                        )
                    }
                }
            }

            // 2. Edit Display Name & Profile Picture Card
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Edit public & session profile",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.profile_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                    )

                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = {
                            if (it.length <= 120) {
                                displayNameInput = it
                                localError = null
                            }
                        },
                        enabled = !isBusy,
                        label = { Text("Display name *") },
                        placeholder = { Text("Enter your display name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Badge,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.mutedInk,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_display_name_input"),
                    )

                    OutlinedTextField(
                        value = photoUrlInput,
                        onValueChange = { next ->
                            if (next.length <= 1000) {
                                photoUrlInput = next
                                clearedPhoto = false
                                if (next.isNotBlank()) stagedPhotoBase64 = null
                                localError = null
                            }
                        },
                        enabled = !isBusy,
                        label = { Text("Profile picture URL (optional https://)") },
                        placeholder = { Text("https://example.com/avatar.jpg") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = TrolleyTheme.colors.mutedInk,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_photo_url_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val pickInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isBusy,
                            interactionSource = pickInteraction,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .weight(1f)
                                .trolleySpringPress(pickInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("pick_profile_photo_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.profile_choose_photo))
                        }

                        if (!effectivePhotoBase64.isNullOrBlank() || !effectivePhotoUrl.isNullOrBlank()) {
                            val clearInteraction = remember { MutableInteractionSource() }
                            TextButton(
                                onClick = {
                                    stagedPhotoBase64 = null
                                    photoUrlInput = ""
                                    clearedPhoto = true
                                    localError = null
                                },
                                enabled = !isBusy,
                                interactionSource = clearInteraction,
                                modifier = Modifier
                                    .trolleySpringPress(clearInteraction)
                                    .minimumInteractiveComponentSize()
                                    .testTag("clear_profile_photo_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.orange,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.profile_remove_photo),
                                    color = TrolleyTheme.colors.orange,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = stagedPhotoBase64 != null) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = TrolleyTheme.colors.limeSoft,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "New device photo selected. Tap Save profile changes to apply.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TrolleyTheme.colors.limeAccent,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }

                    val saveInteraction = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            val cleanName = displayNameInput.trim()
                            val cleanUrl = photoUrlInput.trim().ifBlank { null }
                            if (cleanName.isEmpty()) {
                                localError = "Please enter a display name."
                                return@Button
                            }
                            if (cleanUrl != null && !cleanUrl.startsWith("https://")) {
                                localError = "Photo URL must start with https://"
                                return@Button
                            }
                            localError = null
                            viewModel.updateUserProfile(
                                displayName = cleanName,
                                httpsPhotoUrl = if (clearedPhoto) null else cleanUrl,
                                photoBase64 = if (clearedPhoto) null else effectivePhotoBase64,
                                clearAuthPhoto = clearedPhoto,
                            )
                        },
                        enabled = !isBusy && displayNameInput.trim().isNotEmpty(),
                        interactionSource = saveInteraction,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .trolleySpringPress(saveInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("save_profile_button"),
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Saving profile…")
                        } else {
                            Text(
                                text = stringResource(R.string.profile_save_changes),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    val activeNotice = localError?.let { MutationNotice(it, isError = true) } ?: notice
                    AnimatedVisibility(visible = activeNotice != null) {
                        if (activeNotice != null) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (activeNotice.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (activeNotice.isError) MaterialTheme.colorScheme.error else TrolleyTheme.colors.hairline,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = activeNotice.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (activeNotice.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .testTag("profile_notice_text"),
                                )
                            }
                        }
                    }
                }
            }

            // 3. Authenticated Session & Account Details Card
            Surface(
                shape = RoundedCornerShape(19.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Firebase Auth & Account Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    AccountDetailRow(
                        icon = Icons.Rounded.AlternateEmail,
                        label = "Email address",
                        value = firebaseUser.email ?: "Not provided",
                    )
                    HorizontalDivider(color = TrolleyTheme.colors.hairline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AccountDetailRow(
                            icon = Icons.Rounded.Badge,
                            label = "User ID",
                            value = firebaseUser.uid,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Trolley User ID", firebaseUser.uid))
                                copiedUidNotice = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("copy_uid_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy User ID",
                                tint = TrolleyTheme.colors.mutedInk,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    if (copiedUidNotice) {
                        Text(
                            text = "User ID copied to clipboard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.limeAccent,
                        )
                    }

                    HorizontalDivider(color = TrolleyTheme.colors.hairline)

                    val createdMillis = firebaseUser.metadata?.creationTimestamp
                    val lastSignInMillis = firebaseUser.metadata?.lastSignInTimestamp
                    AccountDetailRow(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Account created",
                        value = formatSessionTimestamp(createdMillis),
                    )
                    HorizontalDivider(color = TrolleyTheme.colors.hairline)

                    AccountDetailRow(
                        icon = Icons.Rounded.Timer,
                        label = "Last sign-in",
                        value = formatSessionTimestamp(lastSignInMillis),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (remainingFreshMs <= 0L) {
                        val refreshInteraction = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                if (isRefreshingSession) return@OutlinedButton
                                isRefreshingSession = true
                                scope.launch {
                                    val result = AuthManager.signInInteractive(context)
                                    isRefreshingSession = false
                                    if (result is GoogleSignInResult.Success) {
                                        remainingFreshMs = AuthManager.remainingFreshVerificationMillis()
                                    }
                                }
                            },
                            enabled = !isBusy && !isRefreshingSession,
                            interactionSource = refreshInteraction,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TrolleyTheme.colors.orange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trolleySpringPress(refreshInteraction)
                                .minimumInteractiveComponentSize()
                                .testTag("profile_reverify_button"),
                        ) {
                            Text("Refresh 10-minute verification window", color = TrolleyTheme.colors.orange)
                        }
                    }

                    val signOutInteraction = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                AuthManager.signOut(context)
                                onSignedOut()
                            }
                        },
                        enabled = !isBusy,
                        interactionSource = signOutInteraction,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .trolleySpringPress(signOutInteraction)
                            .minimumInteractiveComponentSize()
                            .testTag("profile_sign_out_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.sign_out))
                    }

                    TextButton(
                        onClick = onContactClick,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .testTag("profile_contact_support_button"),
                    ) {
                        Text(
                            text = "Account help · ${stringResource(R.string.support_email)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAvatarImage(
    displayName: String,
    photoBase64: String?,
    photoUrl: String?,
    modifier: Modifier = Modifier,
) {
    val decodedBitmap = remember(photoBase64) {
        if (photoBase64.isNullOrBlank()) {
            null
        } else {
            runCatching {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    val initials = remember(displayName) {
        displayName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "T" }
    }

    Box(
        modifier = modifier.background(TrolleyTheme.colors.lime),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = TrolleyTheme.colors.limeDeep,
        )

        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = "$displayName profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "$displayName profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun StatusPill(
    icon: ImageVector,
    text: String,
    highlighted: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) TrolleyTheme.colors.limeSoft else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.mutedInk,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AccountDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TrolleyTheme.colors.mutedInk,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TrolleyTheme.colors.mutedInk,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatSessionTimestamp(millis: Long?): String {
    if (millis == null || millis <= 0L) return "Not available"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

private suspend fun encodeAvatarFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val decoded = BitmapFactory.decodeStream(stream) ?: return@runCatching null
            val side = min(decoded.width, decoded.height)
            val xOffset = (decoded.width - side) / 2
            val yOffset = (decoded.height - side) / 2
            val square = Bitmap.createBitmap(decoded, xOffset, yOffset, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 192, 192, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, out)
            if (square !== decoded) square.recycle()
            if (scaled !== square) scaled.recycle()
            decoded.recycle()
            val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= 80_000) encoded else null
        }
    }.getOrNull()
}
