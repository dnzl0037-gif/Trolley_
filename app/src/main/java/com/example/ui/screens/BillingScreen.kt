package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.catalog.BillingCatalog
import com.example.data.catalog.BillingCycle
import com.example.data.catalog.PaymentMethodType
import com.example.data.catalog.SubscriptionTier
import com.example.data.catalog.formatUsdCents
import com.example.data.model.BillingPayment
import com.example.data.model.ProviderSecurityConfig
import com.example.data.model.ProviderSubscription
import com.example.data.model.UiState
import com.example.ui.components.FinancialTwoFactorDialog
import com.example.ui.components.trolleySpringPress
import com.example.ui.theme.TrolleyTheme
import com.example.ui.viewmodel.BillingDashboardData
import com.example.ui.viewmodel.BillingViewModel
import com.example.ui.viewmodel.MutationNotice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BillingScreen(
    viewModel: BillingViewModel,
    onCreateProviderFirst: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val state by viewModel.billingState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val twoFactorChallenge by viewModel.twoFactorChallenge.collectAsStateWithLifecycle()

    var selectedCycleId by rememberSaveable { mutableStateOf(BillingCycle.MONTHLY.id) }
    var selectedTierId by rememberSaveable { mutableStateOf(SubscriptionTier.FREE_STARTER.id) }
    var selectedMethodId by rememberSaveable { mutableStateOf(PaymentMethodType.ECOCASH.id) }
    var autoRenew by rememberSaveable { mutableStateOf(true) }

    var mobileNumber by rememberSaveable { mutableStateOf("") }
    var cardholderName by rememberSaveable { mutableStateOf("") }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var cardExpiry by rememberSaveable { mutableStateOf("") }
    var cardCvc by rememberSaveable { mutableStateOf("") }
    var paypalEmail by rememberSaveable { mutableStateOf("") }
    var bankAccountRef by rememberSaveable { mutableStateOf("") }

    val activeSub = (state as? UiState.Success)?.data?.subscription
    LaunchedEffect(activeSub?.planTier, activeSub?.billingCycle, activeSub?.paymentMethod) {
        if (activeSub != null) {
            selectedTierId = activeSub.planTier
            selectedCycleId = activeSub.billingCycle
            if (activeSub.paymentMethod != PaymentMethodType.NONE.id) {
                selectedMethodId = activeSub.paymentMethod
            }
            autoRenew = activeSub.autoRenew
        }
    }

    val selectedCycle = BillingCycle.fromId(selectedCycleId) ?: BillingCycle.MONTHLY
    val selectedTier = SubscriptionTier.fromId(selectedTierId) ?: SubscriptionTier.FREE_STARTER
    val selectedMethod = PaymentMethodType.fromId(selectedMethodId) ?: PaymentMethodType.ECOCASH

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 18.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("billing_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.billing_eyebrow),
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.orange,
                    )
                    Text(
                        text = stringResource(R.string.billing_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Text(
                text = stringResource(R.string.billing_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = TrolleyTheme.colors.mutedInk,
            )

            when (val current = state) {
                is UiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Loading subscription & invoice ledger…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                is UiState.Error -> {
                    BillingNoticeCard(MutationNotice(current.message, isError = true))
                }

                is UiState.Success -> {
                    val dashboard: BillingDashboardData = current.data
                    val profile = dashboard.providerProfile

                    if (profile == null) {
                        // Gate if provider profile does not yet exist
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Storefront,
                                    contentDescription = null,
                                    tint = TrolleyTheme.colors.orange,
                                    modifier = Modifier.size(32.dp),
                                )
                                Text(
                                    text = "Create your provider profile first",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Provider billing plans link directly to your verified Trolley business profile. Create your provider draft first—it takes less than a minute.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                                Button(
                                    onClick = onCreateProviderFirst,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("billing_create_provider_cta"),
                                ) {
                                    Text("Set up provider profile")
                                }
                            }
                        }
                    } else {
                        // Active subscription status banner
                        ActiveSubscriptionBanner(
                            businessName = profile.businessName,
                            subscription = dashboard.subscription,
                        )

                        // Firebase Auth + TOTP 2FA Security Banner
                        TwoFactorSecurityStatusCard(
                            securityConfig = dashboard.securityConfig,
                            isLocalEnrolled = remember(twoFactorChallenge, dashboard.securityConfig) {
                                viewModel.isLocalTwoFactorEnrolled()
                            },
                            onConfigureTwoFactor = { viewModel.openTwoFactorSetupDialog() },
                        )

                        // Monthly vs Yearly (30% discount) Tabs
                        BillingCycleTabs(
                            selectedCycle = selectedCycle,
                            onSelectCycle = {
                                selectedCycleId = it.id
                                viewModel.clearNotice()
                            },
                        )

                        // 3 Subscription Tier Cards
                        SubscriptionTier.entries.forEach { tier ->
                            SubscriptionTierCard(
                                tier = tier,
                                cycle = selectedCycle,
                                selected = selectedTier == tier,
                                isCurrentActivePlan = dashboard.subscription?.planTier == tier.id &&
                                    dashboard.subscription.billingCycle == selectedCycle.id,
                                onSelect = {
                                    selectedTierId = tier.id
                                    viewModel.clearNotice()
                                },
                            )
                        }

                        // Payment Method Selection & Checkout Section (when paid tier selected, or show summary for Free Tier)
                        if (selectedTier != SubscriptionTier.FREE_STARTER) {
                            PaymentSystemSelectorCard(
                                selectedMethod = selectedMethod,
                                onSelectMethod = {
                                    selectedMethodId = it.id
                                    viewModel.clearNotice()
                                },
                                mobileNumber = mobileNumber,
                                onMobileNumberChange = { mobileNumber = it },
                                cardholderName = cardholderName,
                                onCardholderNameChange = { cardholderName = it },
                                cardNumber = cardNumber,
                                onCardNumberChange = { cardNumber = it },
                                cardExpiry = cardExpiry,
                                onCardExpiryChange = { cardExpiry = it },
                                cardCvc = cardCvc,
                                onCardCvcChange = { cardCvc = it },
                                paypalEmail = paypalEmail,
                                onPaypalEmailChange = { paypalEmail = it },
                                bankAccountRef = bankAccountRef,
                                onBankAccountRefChange = { bankAccountRef = it },
                                autoRenew = autoRenew,
                                onAutoRenewChange = { autoRenew = it },
                            )
                        }

                        // Checkout Summary & Activate Button
                        val quote = BillingCatalog.computeQuote(selectedTier, selectedCycle)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "ORDER SUMMARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrolleyTheme.colors.orange,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${selectedTier.title} (${selectedCycle.label})",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = quote.formattedSubtotal,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                }
                                if (quote.discountCents > 0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Annual 30% discount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TrolleyTheme.colors.limeAccent,
                                        )
                                        Text(
                                            text = "-${quote.formattedSavings}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            color = TrolleyTheme.colors.limeAccent,
                                        )
                                    }
                                }
                                HorizontalDivider(color = TrolleyTheme.colors.hairline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Total due today",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                    Text(
                                        text = quote.formattedPrice,
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = TrolleyTheme.colors.orange,
                                        modifier = Modifier.testTag("billing_total_amount"),
                                    )
                                }

                                val ctaInteraction = remember { MutableInteractionSource() }
                                Button(
                                    onClick = {
                                        viewModel.initiatePlanActivation(
                                            businessName = profile.businessName,
                                            tier = selectedTier,
                                            cycle = selectedCycle,
                                            method = if (selectedTier == SubscriptionTier.FREE_STARTER) PaymentMethodType.NONE else selectedMethod,
                                            autoRenew = autoRenew,
                                            hasExistingSubscription = dashboard.subscription != null,
                                            mobileNumber = mobileNumber,
                                            cardholderName = cardholderName,
                                            cardNumber = cardNumber,
                                            cardExpiry = cardExpiry,
                                            cardCvc = cardCvc,
                                            paypalEmail = paypalEmail,
                                            bankAccountRef = bankAccountRef,
                                        )
                                        // Wipe raw CVC from in-memory UI state immediately on submit
                                        cardCvc = ""
                                    },
                                    enabled = !isBusy,
                                    interactionSource = ctaInteraction,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .trolleySpringPress(ctaInteraction)
                                        .minimumInteractiveComponentSize()
                                        .testTag("billing_confirm_button"),
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Securing & saving…")
                                    } else {
                                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (selectedTier == SubscriptionTier.FREE_STARTER) {
                                                "Activate Starter Free ($0.00)"
                                            } else {
                                                "${stringResource(R.string.billing_confirm_cta)} (${quote.formattedPrice})"
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (notice != null) {
                            BillingNoticeCard(notice!!)
                        }

                        // Immutable Invoice Ledger History
                        BillingHistoryCard(payments = dashboard.payments)
                    }
                }
            }
        }
    }

    val activeChallenge = twoFactorChallenge
    if (activeChallenge != null) {
        FinancialTwoFactorDialog(
            state = activeChallenge,
            onRequestFirebaseReauth = { viewModel.triggerFirebaseStepUpReauth(context) },
            onSubmitCode = { code -> viewModel.submitTwoFactorVerification(code) },
            onDismiss = { viewModel.dismissTwoFactorChallenge() },
        )
    }
}

@Composable
private fun ActiveSubscriptionBanner(
    businessName: String,
    subscription: ProviderSubscription?,
) {
    val activeTier = SubscriptionTier.fromId(subscription?.planTier.orEmpty()) ?: SubscriptionTier.FREE_STARTER
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.limeAccent),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_active_plan_banner"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (subscription == null) "NO PLAN ACTIVATED YET" else "CURRENT PROVIDER PLAN",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.limeAccent,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = TrolleyTheme.colors.lime,
                ) {
                    Text(
                        text = subscription?.subscriptionStatus?.replace('_', ' ')?.uppercase() ?: "SETUP REQUIRED",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.limeDeep,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Text(
                text = "$businessName · ${if (subscription == null) "Choose your plan below" else activeTier.title}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (subscription != null) {
                val limitText = if (subscription.monthlyBookingLimit >= 9999) {
                    "Unlimited bookings/mo"
                } else {
                    "${subscription.monthlyBookingLimit} bookings/mo limit"
                }
                Text(
                    text = "${formatUsdCents(subscription.amountCents)}/${subscription.billingCycle} · $limitText · Up to ${subscription.maxCategories} categories · ${subscription.maskedInstrument ?: "Free"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                )
            }
        }
    }
}

@Composable
private fun TwoFactorSecurityStatusCard(
    securityConfig: ProviderSecurityConfig?,
    isLocalEnrolled: Boolean,
    onConfigureTwoFactor: () -> Unit,
) {
    val activeBoth = securityConfig?.twoFactorEnabled == true && isLocalEnrolled
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (activeBoth) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.orange.copy(alpha = 0.45f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_twofa_status_card"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = if (activeBoth) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.orange,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "FINANCIAL 2FA PROTECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeBoth) TrolleyTheme.colors.limeAccent else TrolleyTheme.colors.orange,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (activeBoth) TrolleyTheme.colors.lime else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = if (activeBoth) "ENABLED" else "ENROLLMENT NEEDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeBoth) TrolleyTheme.colors.limeDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = if (activeBoth) {
                    "Firebase Auth step-up + Android Keystore TOTP (6-digit) protects paid plan upgrades and billing changes."
                } else {
                    "All paid checkout actions require Firebase Auth re-verification plus a 6-digit Authenticator code."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TrolleyTheme.colors.mutedInk,
            )
            OutlinedButton(
                onClick = onConfigureTwoFactor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("billing_manage_2fa_button"),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLocalEnrolled) "Rotate or Re-verify 2FA Key" else "Set Up Provider 2FA Now")
            }
        }
    }
}

@Composable
private fun BillingCycleTabs(
    selectedCycle: BillingCycle,
    onSelectCycle: (BillingCycle) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BillingCycle.entries.forEach { cycle ->
                val active = selectedCycle == cycle
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(if (cycle == BillingCycle.MONTHLY) "billing_tab_monthly" else "billing_tab_yearly")
                        .clickable { onSelectCycle(cycle) },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                    ) {
                        Text(
                            text = if (cycle == BillingCycle.MONTHLY) {
                                stringResource(R.string.billing_monthly_tab)
                            } else {
                                stringResource(R.string.billing_yearly_tab)
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (active) MaterialTheme.colorScheme.onPrimary else TrolleyTheme.colors.mutedInk,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionTierCard(
    tier: SubscriptionTier,
    cycle: BillingCycle,
    selected: Boolean,
    isCurrentActivePlan: Boolean,
    onSelect: () -> Unit,
) {
    val quote = BillingCatalog.computeQuote(tier, cycle)
    val borderColor = when {
        selected -> TrolleyTheme.colors.orange
        isCurrentActivePlan -> TrolleyTheme.colors.limeAccent
        else -> TrolleyTheme.colors.hairline
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_tier_${tier.id}")
            .clickable { onSelect() },
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (tier == SubscriptionTier.BUSINESS_ELITE) {
                            TrolleyTheme.colors.orange.copy(alpha = 0.14f)
                        } else {
                            TrolleyTheme.colors.lime
                        },
                    ) {
                        Text(
                            text = tier.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tier == SubscriptionTier.BUSINESS_ELITE) {
                                TrolleyTheme.colors.orange
                            } else {
                                TrolleyTheme.colors.limeDeep
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = tier.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = tier.tagline,
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = quote.formattedPrice,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (tier.monthlyCents == 0) {
                            "Forever free"
                        } else if (cycle == BillingCycle.MONTHLY) {
                            "per month"
                        } else {
                            "per year"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                    if (quote.discountCents > 0) {
                        Text(
                            text = "${quote.formattedSubtotal}/yr",
                            style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough),
                            color = TrolleyTheme.colors.mutedInk,
                        )
                        Text(
                            text = "Save ${quote.formattedSavings} (30%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TrolleyTheme.colors.orange,
                        )
                    }
                }
            }

            HorizontalDivider(color = TrolleyTheme.colors.hairline)

            tier.features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (selected) TrolleyTheme.colors.orange else TrolleyTheme.colors.limeAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (selected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TrolleyTheme.colors.lime,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (isCurrentActivePlan) "Selected · Matches your active plan" else "Selected for checkout",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TrolleyTheme.colors.limeDeep,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentSystemSelectorCard(
    selectedMethod: PaymentMethodType,
    onSelectMethod: (PaymentMethodType) -> Unit,
    mobileNumber: String,
    onMobileNumberChange: (String) -> Unit,
    cardholderName: String,
    onCardholderNameChange: (String) -> Unit,
    cardNumber: String,
    onCardNumberChange: (String) -> Unit,
    cardExpiry: String,
    onCardExpiryChange: (String) -> Unit,
    cardCvc: String,
    onCardCvcChange: (String) -> Unit,
    paypalEmail: String,
    onPaypalEmailChange: (String) -> Unit,
    bankAccountRef: String,
    onBankAccountRefChange: (String) -> Unit,
    autoRenew: Boolean,
    onAutoRenewChange: (Boolean) -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TrolleyTheme.colors.limeAccent,
        unfocusedBorderColor = TrolleyTheme.colors.hairline,
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "PAYMENT METHOD · PCI-SAFE CHECKOUT",
                style = MaterialTheme.typography.labelSmall,
                color = TrolleyTheme.colors.orange,
            )
            Text(
                text = "Primary Rails: EcoCash, Visa & Mastercard",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentMethodType.featuredMethods.forEach { method ->
                    PaymentChip(
                        method = method,
                        selected = selectedMethod == method,
                        icon = when (method) {
                            PaymentMethodType.ECOCASH -> Icons.Rounded.PhoneAndroid
                            PaymentMethodType.VISA, PaymentMethodType.MASTERCARD -> Icons.Rounded.CreditCard
                            else -> Icons.Rounded.AccountBalanceWallet
                        },
                        onClick = { onSelectMethod(method) },
                    )
                }
            }

            Text(
                text = "Other Billing Systems",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TrolleyTheme.colors.mutedInk,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaymentMethodType.otherMethods.forEach { method ->
                    PaymentChip(
                        method = method,
                        selected = selectedMethod == method,
                        icon = when (method) {
                            PaymentMethodType.INNBUCKS -> Icons.Rounded.PhoneAndroid
                            PaymentMethodType.PAYPAL -> Icons.Rounded.AccountBalanceWallet
                            PaymentMethodType.BANK_EFT -> Icons.Rounded.AccountBalance
                            else -> Icons.Rounded.CreditCard
                        },
                        onClick = { onSelectMethod(method) },
                    )
                }
            }

            HorizontalDivider(color = TrolleyTheme.colors.hairline)

            when (selectedMethod) {
                PaymentMethodType.ECOCASH -> {
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { if (it.length <= 16) onMobileNumberChange(it) },
                        label = { Text("EcoCash Zimbabwe mobile number *") },
                        placeholder = { Text("0771234567 or +263771234567") },
                        supportingText = { Text("Accepts 077 / 078 / +263 numbers. Only masked digits are saved.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_ecocash_input"),
                    )
                }

                PaymentMethodType.INNBUCKS -> {
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = { if (it.length <= 16) onMobileNumberChange(it) },
                        label = { Text("InnBucks wallet mobile number *") },
                        placeholder = { Text("0771234567 or 0711234567") },
                        supportingText = { Text("Generates an InnBucks settlement authorization code.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_innbucks_input"),
                    )
                }

                PaymentMethodType.VISA, PaymentMethodType.MASTERCARD -> {
                    OutlinedTextField(
                        value = cardholderName,
                        onValueChange = { if (it.length <= 80) onCardholderNameChange(it) },
                        label = { Text("Cardholder name *") },
                        placeholder = { Text("Name as shown on ${selectedMethod.displayName}") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_cardholder_input"),
                    )
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { next ->
                            val clean = next.filter { it.isDigit() || it == ' ' }
                            if (clean.length <= 23) onCardNumberChange(clean)
                        },
                        label = { Text("${selectedMethod.displayName} number *") },
                        placeholder = {
                            Text(if (selectedMethod == PaymentMethodType.VISA) "4xxx xxxx xxxx xxxx" else "54xx xxxx xxxx xxxx")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_card_number_input"),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = { if (it.length <= 5) onCardExpiryChange(it) },
                            label = { Text("Expiry (MM/YY) *") },
                            placeholder = { Text("08/28") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("billing_card_expiry_input"),
                        )
                        OutlinedTextField(
                            value = cardCvc,
                            onValueChange = { next ->
                                val digits = next.filter { it.isDigit() }
                                if (digits.length <= 4) onCardCvcChange(digits)
                            },
                            label = { Text("CVC *") },
                            placeholder = { Text("123") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("billing_card_cvc_input"),
                        )
                    }
                    Text(
                        text = "Validated locally via Luhn + BIN check. Raw card digits and CVC are never saved to Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TrolleyTheme.colors.mutedInk,
                    )
                }

                PaymentMethodType.PAYPAL -> {
                    OutlinedTextField(
                        value = paypalEmail,
                        onValueChange = { if (it.length <= 120) onPaypalEmailChange(it) },
                        label = { Text("PayPal account email *") },
                        placeholder = { Text("business@example.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_paypal_input"),
                    )
                }

                PaymentMethodType.BANK_EFT -> {
                    OutlinedTextField(
                        value = bankAccountRef,
                        onValueChange = { if (it.length <= 24) onBankAccountRefChange(it) },
                        label = { Text("Bank name + last 4 digits *") },
                        placeholder = { Text("e.g. CBZ 4521") },
                        supportingText = { Text("Enter at most 4 trailing digits. Full bank account numbers are rejected.") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("billing_bank_eft_input"),
                    )
                }

                PaymentMethodType.NONE -> Unit
            }

            if (selectedMethod.supportsAutoRenew) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-renew subscription",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Switch back to Free Starter anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrolleyTheme.colors.mutedInk,
                        )
                    }
                    Switch(
                        checked = autoRenew,
                        onCheckedChange = onAutoRenewChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TrolleyTheme.colors.limeDeep,
                            checkedTrackColor = TrolleyTheme.colors.lime,
                        ),
                        modifier = Modifier.testTag("billing_autorenew_switch"),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentChip(
    method: PaymentMethodType,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        label = { Text(method.displayName) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TrolleyTheme.colors.lime,
            selectedLabelColor = TrolleyTheme.colors.limeDeep,
            selectedLeadingIconColor = TrolleyTheme.colors.limeDeep,
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("payment_method_${method.id}"),
    )
}

@Composable
private fun BillingHistoryCard(payments: List<BillingPayment>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("billing_history_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = TrolleyTheme.colors.orange,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "BILLING LEDGER & RECEIPTS (${payments.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TrolleyTheme.colors.orange,
                )
            }

            if (payments.isEmpty()) {
                Text(
                    text = "No invoices recorded yet. Activate Starter Free or any paid plan above to create an immutable invoice receipt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TrolleyTheme.colors.mutedInk,
                )
            } else {
                payments.take(6).forEach { payment ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, TrolleyTheme.colors.hairline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${payment.invoiceNumber} · ${SubscriptionTier.fromId(payment.planTier)?.title ?: payment.planTier}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${payment.maskedInstrument} · ${payment.billingCycle} · ${payment.paymentStatus.replace('_', ' ')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrolleyTheme.colors.mutedInk,
                                )
                            }
                            Text(
                                text = formatUsdCents(payment.amountCents),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillingNoticeCard(notice: MutationNotice) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (notice.isError) MaterialTheme.colorScheme.errorContainer else TrolleyTheme.colors.lime,
        border = BorderStroke(
            1.dp,
            if (notice.isError) MaterialTheme.colorScheme.error else TrolleyTheme.colors.limeDeep.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = notice.message,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer else TrolleyTheme.colors.limeDeep,
            modifier = Modifier
                .padding(14.dp)
                .testTag("billing_notice_text"),
        )
    }
}
