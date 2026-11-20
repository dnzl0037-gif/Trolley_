package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.catalog.GeographyCatalog
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.firebase.AuthManager
import com.example.data.firebase.GoogleSignInResult
import com.example.data.location.AddressGeocoder
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.example.ui.components.PickerOption
import com.example.ui.components.ProviderRatingOverviewCard
import com.example.ui.components.RegionCountryFields
import com.example.ui.components.ReviewFeedbackCard
import com.example.ui.components.SearchablePickerField
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.AccountProviderViewModel
import com.example.ui.viewmodel.MutationNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil

@Composable
fun ProviderScreen(
    viewModel: AccountProviderViewModel,
    userDisplayName: String,
    userEmail: String?,
    onSignedOut: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by viewModel.userAccountState.collectAsStateWithLifecycle()
    val profileState by viewModel.providerProfileState.collectAsStateWithLifecycle()
    val ownReviewsState by viewModel.ownReviewsState.collectAsStateWithLifecycle()
    val ownRatingSummary by viewModel.ownRatingSummary.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    var remainingFreshMs by remember { mutableStateOf(AuthManager.remainingFreshVerificationMillis()) }
    var isRefreshingSession by remember { mutableStateOf(false) }
    var isGeocodingAddress by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            remainingFreshMs = AuthManager.remainingFreshVerificationMillis()
            delay(15_000L)
        }
    }

    val categoryOptions = remember {
        ServiceTaxonomy.categories.map {
            PickerOption(
                value = it.slug,
                label = it.name,
                hint = it.family,
                keywords = it.slug,
            )
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
            // Account status header card
            AccountHeaderCard(
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                accountState = accountState,
                isBusy = isBusy || isRefreshingSession,
                remainingFreshMs = remainingFreshMs,
                onRefreshVerification = {
                    if (isRefreshingSession) return@AccountHeaderCard
                    isRefreshingSession = true
                    scope.launch {
                        val result = AuthManager.signInInteractive(context)
                        isRefreshingSession = false
                        if (result is GoogleSignInResult.Success) {
                            remainingFreshMs = AuthManager.remainingFreshVerificationMillis()
                        }
                    }
                },
                onVerifyAge = { name, dob -> viewModel.verifyAge(name, dob) },
                onSignOut = {
                    scope.launch {
                        AuthManager.signOut(context)
                        onSignedOut()
                    }
                },
            )

            // Provider Profile Draft Card
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
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storefront,
                            contentDescription = null,
                            tint = TrolleyTheme.colors.lime,
                            modifier = Modifier.padding(10.dp),
                        )
                    }

                    Text(
                        text = stringResource(R.string.provider_eyebrow),
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.orange,
                    )

                    when (val state = profileState) {
                        is UiState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 12.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Loading your saved profile…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                        }

                        is UiState.Error -> {
                            Text(
                                text = "Your saved provider profile.",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            NoticeBox(
                                notice = MutationNotice(
                                    message = "We could not check your saved provider profile: ${state.message}",
                                    isError = true,
                                ),
                            )
                            OutlinedButton(
                                onClick = { viewModel.reloadSavedProfile() },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("retry_provider_profile_button"),
                            ) {
                                Text("Retry")
                            }
                        }

                        is UiState.Success -> {
                            val profile = state.data
                            if (profile == null) {
                                CreateProviderDraftForm(
                                    categoryOptions = categoryOptions,
                                    isBusy = isBusy || isGeocodingAddress,
                                    notice = notice,
                                    onSubmit = { name, desc, slug, country, city, address ->
                                        scope.launch {
                                            isGeocodingAddress = true
                                            val coords = if (address.trim().length >= 2) {
                                                AddressGeocoder.geocodeBusinessAddress(
                                                    context = context,
                                                    businessAddress = address,
                                                    city = city,
                                                    countryCode = country,
                                                )
                                            } else {
                                                null
                                            }
                                            isGeocodingAddress = false
                                            viewModel.createProviderDraft(
                                                businessName = name,
                                                description = desc,
                                                categorySlug = slug,
                                                countryCode = country,
                                                city = city,
                                                businessAddress = address,
                                                latitude = coords?.latitude,
                                                longitude = coords?.longitude,
                                            )
                                        }
                                    },
                                )
                            } else {
                                EditProviderDraftForm(
                                    profile = profile,
                                    categoryOptions = categoryOptions,
                                    isBusy = isBusy || isRefreshingSession || isGeocodingAddress,
                                    isSessionFresh = remainingFreshMs > 0L,
                                    notice = notice,
                                    onRefreshVerification = {
                                        if (isRefreshingSession) return@EditProviderDraftForm
                                        isRefreshingSession = true
                                        scope.launch {
                                            val result = AuthManager.signInInteractive(context)
                                            isRefreshingSession = false
                                            if (result is GoogleSignInResult.Success) {
                                                remainingFreshMs = AuthManager.remainingFreshVerificationMillis()
                                            }
                                        }
                                    },
                                    onSave = { name, desc, slug, country, city, address, submit ->
                                        scope.launch {
                                            isGeocodingAddress = true
                                            val coords = if (address.trim().length >= 2) {
                                                AddressGeocoder.geocodeBusinessAddress(
                                                    context = context,
                                                    businessAddress = address,
                                                    city = city,
                                                    countryCode = country,
                                                )
                                            } else {
                                                null
                                            }
                                            isGeocodingAddress = false
                                            viewModel.updateProviderDraft(
                                                currentStatus = profile.listingStatus,
                                                businessName = name,
                                                description = desc,
                                                categorySlug = slug,
                                                countryCode = country,
                                                city = city,
                                                businessAddress = address,
                                                latitude = coords?.latitude,
                                                longitude = coords?.longitude,
                                                submitForReview = submit,
                                            )
                                        }
                                    },
                                    onReload = { viewModel.reloadSavedProfile() },
                                )
                            }
                        }
                    }
                }
            }

            val savedProfile = (profileState as? UiState.Success)?.data
            if (savedProfile != null) {
                Surface(
                    shape = RoundedCornerShape(19.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("own_provider_reviews_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.reviews_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = TrolleyTheme.colors.orange,
                        )
                        Text(
                            text = "${stringResource(R.string.reviews_section_title)} (${ownRatingSummary.reviewCount})",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ProviderRatingOverviewCard(summary = ownRatingSummary)

                        when (val revState = ownReviewsState) {
                            UiState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(
                                        text = "Syncing customer feedback…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrolleyTheme.colors.mutedInk,
                                    )
                                }
                            }
                            is UiState.Error -> {
                                Text(
                                    text = revState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            is UiState.Success -> {
                                if (revState.data.isEmpty()) {
                                    Text(
                                        text = "No customer ratings have been submitted for ${savedProfile.businessName} yet. Reviews from customers appear here with your live average score.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TrolleyTheme.colors.mutedInk,
                                    )
                                } else {
                                    revState.data.take(5).forEach { review ->
                                        ReviewFeedbackCard(
                                            review = review,
                                            isOwnReview = review.customerId == savedProfile.userId,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeaderCard(
    userDisplayName: String,
    userEmail: String?,
    accountState: UiState<UserAccount?>,
    isBusy: Boolean,
    remainingFreshMs: Long,
    onRefreshVerification: () -> Unit,
    onVerifyAge: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val account = (accountState as? UiState.Success)?.data
    val needsAgeGate = accountState is UiState.Success && (account == null || !account.ageVerified)
    val signOutInteraction = remember { MutableInteractionSource() }

    var fullName by rememberSaveable { mutableStateOf(userDisplayName.ifBlank { "Trolley Member" }) }
    var dateOfBirth by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(account?.fullName) {
        if (!account?.fullName.isNullOrBlank()) {
            fullName = account!!.fullName
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.fullName?.ifBlank { userDisplayName } ?: userDisplayName.ifBlank { "Signed in" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    enabled = !isBusy,
                    interactionSource = signOutInteraction,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                    modifier = Modifier
                        .trolleySpringPress(signOutInteraction)
                        .minimumInteractiveComponentSize()
                        .testTag("sign_out_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.sign_out))
                }
            }

            // 10-minute verification window status
            val remainingMinutes = ceil(remainingFreshMs / 60_000.0).toInt()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (remainingFreshMs > 0L) {
                            "Verification window active (${remainingMinutes}m left of 10m)"
                        } else {
                            "10m verification window expired — re-verify before submitting"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (remainingFreshMs > 0L) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.orange,
                        modifier = Modifier.weight(1f),
                    )
                    if (remainingFreshMs <= 0L) {
                        TextButton(
                            onClick = onRefreshVerification,
                            enabled = !isBusy,
                            modifier = Modifier.testTag("refresh_verification_button"),
                        ) {
                            Text("Re-verify", color = TrolleyTheme.colors.orange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (account?.ageVerified == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Age eligibility confirmed (13+)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (needsAgeGate) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Confirm account details (13+)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "You must be at least 13 years old to use Trolley accounts. Under-13 data is not saved.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Full name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("age_gate_name_input"),
                        )
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("age_gate_dob_picker")
                                .clickable {
                                    val cal = Calendar.getInstance()
                                    val currentParsed = runCatching { LocalDate.parse(dateOfBirth) }.getOrNull()
                                    val initYear = currentParsed?.year ?: (cal.get(Calendar.YEAR) - 20)
                                    val initMonth = (currentParsed?.monthValue ?: 1) - 1
                                    val initDay = currentParsed?.dayOfMonth ?: 1
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            dateOfBirth = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                                        },
                                        initYear,
                                        initMonth,
                                        initDay,
                                    ).apply {
                                        datePicker.maxDate = System.currentTimeMillis()
                                    }.show()
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = if (dateOfBirth.isBlank()) "Select date of birth (YYYY-MM-DD)" else "Date of birth: $dateOfBirth",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (dateOfBirth.isBlank()) TrolleyTheme.colors.mutedInk else MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = "Select date of birth",
                                    tint = TrolleyTheme.colors.mutedInk,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        val ageConfirmInteraction = remember { MutableInteractionSource() }
                        Button(
                            onClick = { onVerifyAge(fullName, dateOfBirth) },
                            enabled = !isBusy && fullName.isNotBlank() && dateOfBirth.isNotBlank(),
                            interactionSource = ageConfirmInteraction,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trolleySpringPress(ageConfirmInteraction)
                                .testTag("confirm_age_button"),
                        ) {
                            Text("Confirm age & save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateProviderDraftForm(
    categoryOptions: List<PickerOption>,
    isBusy: Boolean,
    notice: MutationNotice?,
    onSubmit: (businessName: String, description: String, categorySlug: String, countryCode: String, city: String, businessAddress: String) -> Unit,
) {
    var businessName by rememberSaveable { mutableStateOf("") }
    var categorySlug by rememberSaveable { mutableStateOf("plumbing-leak-detection") }
    var countryCode by rememberSaveable { mutableStateOf("ZW") }
    var city by rememberSaveable { mutableStateOf("Harare") }
    var businessAddress by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    Text(
        text = stringResource(R.string.provider_create_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.provider_create_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = TrolleyTheme.colors.mutedInk,
    )

    ProviderFormFields(
        businessName = businessName,
        onBusinessNameChange = { businessName = it },
        categorySlug = categorySlug,
        onCategorySlugChange = { categorySlug = it },
        countryCode = countryCode,
        onCountryCodeChange = { next ->
            if (next != countryCode) city = ""
            countryCode = next
        },
        city = city,
        onCityChange = { city = it },
        businessAddress = businessAddress,
        onBusinessAddressChange = { businessAddress = it },
        description = description,
        onDescriptionChange = { description = it },
        categoryOptions = categoryOptions,
        enabled = !isBusy,
    )

    val addressValid = businessAddress.isBlank() || businessAddress.trim().length in 2..200
    val saveDraftInteraction = remember { MutableInteractionSource() }
    Button(
        onClick = { onSubmit(businessName, description, categorySlug, countryCode, city, businessAddress) },
        enabled = !isBusy && businessName.trim().length >= 2 && city.trim().length >= 2 && countryCode.length == 2 && categorySlug.isNotBlank() && addressValid,
        interactionSource = saveDraftInteraction,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .trolleySpringPress(saveDraftInteraction)
            .minimumInteractiveComponentSize()
            .testTag("save_provider_button"),
    ) {
        Text(if (isBusy) "Saving draft…" else stringResource(R.string.save_provider_draft))
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }

    if (notice != null) {
        NoticeBox(notice = notice)
    }
}

@Composable
private fun EditProviderDraftForm(
    profile: ProviderProfile,
    categoryOptions: List<PickerOption>,
    isBusy: Boolean,
    isSessionFresh: Boolean,
    notice: MutationNotice?,
    onRefreshVerification: () -> Unit,
    onSave: (businessName: String, description: String, categorySlug: String, countryCode: String, city: String, businessAddress: String, submit: Boolean) -> Unit,
    onReload: () -> Unit,
) {
    var businessName by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.businessName)
    }
    var categorySlug by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.categorySlug)
    }
    var countryCode by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.countryCode)
    }
    var city by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.city)
    }
    var businessAddress by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.businessAddress.orEmpty())
    }
    var description by rememberSaveable(profile.id, profile.updatedAt?.seconds) {
        mutableStateOf(profile.description.orEmpty())
    }

    Text(
        text = stringResource(R.string.provider_edit_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Listing status: ${profile.listingStatus.replace('_', ' ')}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("listing_status_text"),
            )
            Text(
                text = "Verification: ${profile.verificationStatus.replace('_', ' ')}. Only Trolley administrators can approve verification or publish your listing. Submitting for review does not make it public.",
                style = MaterialTheme.typography.bodySmall,
                color = TrolleyTheme.colors.mutedInk,
            )
        }
    }

    if (!profile.isEditable) {
        NoticeBox(
            notice = MutationNotice(
                message = "This profile cannot be edited here. Editing supports draft and pending-review listings.",
                isError = true,
            ),
        )
    } else {
        ProviderFormFields(
            businessName = businessName,
            onBusinessNameChange = { businessName = it },
            categorySlug = categorySlug,
            onCategorySlugChange = { categorySlug = it },
            countryCode = countryCode,
            onCountryCodeChange = { next ->
                if (next != countryCode) city = ""
                countryCode = next
            },
            city = city,
            onCityChange = { city = it },
            businessAddress = businessAddress,
            onBusinessAddressChange = { businessAddress = it },
            description = description,
            onDescriptionChange = { description = it },
            categoryOptions = categoryOptions,
            enabled = !isBusy,
        )

        val addressValid = businessAddress.isBlank() || businessAddress.trim().length in 2..200
        val valid = businessName.trim().length >= 2 &&
            city.trim().length >= 2 &&
            countryCode.length == 2 &&
            categorySlug.isNotBlank() &&
            addressValid

        val saveChangesInteraction = remember { MutableInteractionSource() }
        Button(
            onClick = { onSave(businessName, description, categorySlug, countryCode, city, businessAddress, false) },
            enabled = !isBusy && valid,
            interactionSource = saveChangesInteraction,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .trolleySpringPress(saveChangesInteraction)
                .minimumInteractiveComponentSize()
                .testTag("save_changes_button"),
        ) {
            Text(if (isBusy) "Saving…" else stringResource(R.string.save_changes))
        }

        val submitReviewInteraction = remember { MutableInteractionSource() }
        OutlinedButton(
            onClick = {
                if (isSessionFresh) {
                    onSave(businessName, description, categorySlug, countryCode, city, businessAddress, true)
                } else {
                    onRefreshVerification()
                }
            },
            enabled = !isBusy && valid && profile.listingStatus == "draft",
            interactionSource = submitReviewInteraction,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .trolleySpringPress(submitReviewInteraction)
                .minimumInteractiveComponentSize()
                .testTag("submit_review_button"),
        ) {
            Text(
                if (isSessionFresh || profile.listingStatus != "draft") {
                    stringResource(R.string.submit_for_review)
                } else {
                    "Re-verify session to submit"
                }
            )
        }

        if (profile.listingStatus == "pending_review") {
            Text(
                text = "Your listing is awaiting review. Saved changes remain pending review.",
                style = MaterialTheme.typography.bodySmall,
                color = TrolleyTheme.colors.mutedInk,
            )
        }
    }

    if (notice != null) {
        NoticeBox(notice = notice)
    }

    TextButton(
        onClick = onReload,
        enabled = !isBusy,
        modifier = Modifier.testTag("reload_saved_details_button"),
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(stringResource(R.string.reload_saved_details))
    }

    Text(
        text = "Saved reference: ${profile.id}. These details were loaded from your account.",
        style = MaterialTheme.typography.bodySmall,
        color = TrolleyTheme.colors.mutedInk,
    )
}

@Composable
private fun ProviderFormFields(
    businessName: String,
    onBusinessNameChange: (String) -> Unit,
    categorySlug: String,
    onCategorySlugChange: (String) -> Unit,
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    city: String,
    onCityChange: (String) -> Unit,
    businessAddress: String,
    onBusinessAddressChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    categoryOptions: List<PickerOption>,
    enabled: Boolean,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TrolleyTheme.colors.limeAccent,
        unfocusedBorderColor = TrolleyTheme.colors.hairline,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background,
    )

    OutlinedTextField(
        value = businessName,
        onValueChange = { if (it.length <= 120) onBusinessNameChange(it) },
        enabled = enabled,
        label = { Text("Business name *") },
        placeholder = { Text("e.g. Brightline Electrical") },
        singleLine = true,
        shape = RoundedCornerShape(9.dp),
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_business_name_input"),
    )

    SearchablePickerField(
        label = "Main service category",
        value = categorySlug,
        options = categoryOptions,
        onSelect = onCategorySlugChange,
        placeholder = "Choose a service category",
        searchPlaceholder = "Search service or family",
        enabled = enabled,
        required = true,
        testTag = "provider_category_picker",
    )

    RegionCountryFields(
        countryCode = countryCode,
        onCountryChange = onCountryCodeChange,
        enabled = enabled,
        required = true,
        testTagPrefix = "provider",
    )

    val countryLabel = remember(countryCode) {
        if (countryCode.isNotBlank()) GeographyCatalog.getCountryLabel(countryCode) else ""
    }

    OutlinedTextField(
        value = city,
        onValueChange = { if (it.length <= 100) onCityChange(it) },
        enabled = enabled,
        label = { Text("City *") },
        placeholder = {
            Text(
                if (countryLabel.isNotBlank()) "A city in $countryLabel" else "Choose a country first",
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(9.dp),
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_city_input"),
    )

    OutlinedTextField(
        value = businessAddress,
        onValueChange = { if (it.length <= 200) onBusinessAddressChange(it) },
        enabled = enabled,
        label = { Text("Business street address (for map pin)") },
        placeholder = { Text("e.g. 14 Samora Machel Ave, Avondale") },
        supportingText = { Text("Used to place your business marker on Trolley's nearby map.") },
        singleLine = true,
        shape = RoundedCornerShape(9.dp),
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_address_input"),
    )

    OutlinedTextField(
        value = description,
        onValueChange = { if (it.length <= 1200) onDescriptionChange(it) },
        enabled = enabled,
        label = { Text("Short business description") },
        placeholder = { Text("Tell customers what you do and the areas you serve.") },
        minLines = 3,
        maxLines = 5,
        shape = RoundedCornerShape(9.dp),
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_description_input"),
    )
}

@Composable
private fun NoticeBox(notice: MutationNotice) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (notice.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (notice.isError) MaterialTheme.colorScheme.error else TrolleyTheme.colors.hairline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = notice.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(12.dp)
                .testTag("provider_notice_text"),
        )
    }
}
