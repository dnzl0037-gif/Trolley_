package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.catalog.BillingCycle
import com.example.data.catalog.PaymentMethodType
import com.example.data.catalog.PaymentValidator
import com.example.data.catalog.SubscriptionTier
import com.example.data.firebase.AuthManager
import com.example.data.firebase.FinancialStepUpResult
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.BillingPayment
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderSecurityConfig
import com.example.data.model.ProviderSubscription
import com.example.data.model.UiState
import com.example.data.security.LocalTwoFactorStatus
import com.example.data.security.TotpVerifyOutcome
import com.example.data.security.TwoFactorAuthenticator
import com.example.data.security.TwoFactorSecretStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BillingDashboardData(
    val providerProfile: ProviderProfile?,
    val subscription: ProviderSubscription?,
    val payments: List<BillingPayment>,
    val securityConfig: ProviderSecurityConfig?,
)

data class PendingFinancialCheckout(
    val businessName: String,
    val tier: SubscriptionTier,
    val cycle: BillingCycle,
    val method: PaymentMethodType,
    val autoRenew: Boolean,
    val maskedInstrument: String,
    val mobileNumber: String,
    val cardholderName: String,
    val cardNumber: String,
    val cardExpiry: String,
    val cardCvc: String,
    val paypalEmail: String,
    val bankAccountRef: String,
    val isActionType: String,
)

data class TwoFactorChallengeUiState(
    val pendingCheckout: PendingFinancialCheckout? = null,
    val isSetupOnlyMode: Boolean = false,
    val accountEmail: String = "",
    val localTwoFactorStatus: LocalTwoFactorStatus = LocalTwoFactorStatus(),
    val enrollmentSecretBase32: String? = null,
    val otpAuthUri: String? = null,
    val remainingReauthMillis: Long = 0L,
    val firebaseAuthTimeSeconds: Long? = null,
    val enrolledFirebaseFactorsCount: Int = 0,
    val isReauthenticating: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class BillingViewModel(
    private val repository: TrolleyRepository,
    private val secretStore: TwoFactorSecretStore,
    private val currentUserId: String,
    private val userEmail: String? = null,
) : ViewModel() {

    val billingState: StateFlow<UiState<BillingDashboardData>> = combine(
        repository.observeProviderProfile(currentUserId),
        repository.observeProviderSubscription(currentUserId),
        repository.observeBillingPayments(currentUserId),
        repository.observeProviderSecurityConfig(currentUserId),
    ) { profile, subscription, payments, securityConfig ->
        UiState.Success(
            BillingDashboardData(
                providerProfile = profile,
                subscription = subscription,
                payments = payments,
                securityConfig = securityConfig,
            )
        ) as UiState<BillingDashboardData>
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing billing state", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Failed to load billing details."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading,
        )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _notice = MutableStateFlow<MutationNotice?>(null)
    val notice: StateFlow<MutationNotice?> = _notice.asStateFlow()

    private val _twoFactorChallenge = MutableStateFlow<TwoFactorChallengeUiState?>(null)
    val twoFactorChallenge: StateFlow<TwoFactorChallengeUiState?> = _twoFactorChallenge.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun dismissTwoFactorChallenge() {
        _twoFactorChallenge.value = null
    }

    fun isLocalTwoFactorEnrolled(): Boolean = secretStore.getStatus(currentUserId).isEnrolled

    fun openTwoFactorSetupDialog() {
        viewModelScope.launch {
            val status = secretStore.getStatus(currentUserId)
            val newSecret = TwoFactorAuthenticator.generateSecretBase32()
            val uri = TwoFactorAuthenticator.buildOtpAuthUri(
                accountEmailOrLabel = userEmail ?: currentUserId,
                secretBase32 = newSecret,
            )
            val remainingReauth = AuthManager.remainingFinancialReauthMillis()
            val authTime = if (remainingReauth > 0L) AuthManager.currentFirebaseAuthTimeSeconds() else null
            _twoFactorChallenge.value = TwoFactorChallengeUiState(
                pendingCheckout = null,
                isSetupOnlyMode = true,
                accountEmail = userEmail ?: currentUserId,
                localTwoFactorStatus = status,
                enrollmentSecretBase32 = newSecret,
                otpAuthUri = uri,
                remainingReauthMillis = remainingReauth,
                firebaseAuthTimeSeconds = authTime,
                enrolledFirebaseFactorsCount = AuthManager.enrolledFirebaseMfaCount(),
            )
        }
    }

    fun initiatePlanActivation(
        businessName: String,
        tier: SubscriptionTier,
        cycle: BillingCycle,
        method: PaymentMethodType,
        autoRenew: Boolean,
        hasExistingSubscription: Boolean,
        mobileNumber: String = "",
        cardholderName: String = "",
        cardNumber: String = "",
        cardExpiry: String = "",
        cardCvc: String = "",
        paypalEmail: String = "",
        bankAccountRef: String = "",
    ) {
        if (_isBusy.value) return
        _notice.value = null

        // Free tier requires no paid financial transaction 2FA barrier
        if (tier == SubscriptionTier.FREE_STARTER) {
            executeFreeActivation(businessName, cycle)
            return
        }

        // Validate payment details locally BEFORE launching 2FA step-up modal
        val validation = PaymentValidator.validateAndMask(
            tier = tier,
            method = method,
            mobileNumber = mobileNumber,
            cardholderName = cardholderName,
            cardNumber = cardNumber,
            cardExpiry = cardExpiry,
            cardCvc = cardCvc,
            paypalEmail = paypalEmail,
            bankAccountLast4OrRef = bankAccountRef,
        )
        if (!validation.isValid) {
            _notice.value = MutationNotice(
                message = validation.errorMessage ?: "Payment details failed security validation.",
                isError = true,
            )
            return
        }

        val actionType = if (hasExistingSubscription) "switch_plan" else "activate_subscription"
        val pending = PendingFinancialCheckout(
            businessName = businessName,
            tier = tier,
            cycle = cycle,
            method = method,
            autoRenew = autoRenew,
            maskedInstrument = validation.maskedInstrument,
            mobileNumber = mobileNumber,
            cardholderName = cardholderName,
            cardNumber = cardNumber,
            cardExpiry = cardExpiry,
            cardCvc = cardCvc,
            paypalEmail = paypalEmail,
            bankAccountRef = bankAccountRef,
            isActionType = actionType,
        )

        viewModelScope.launch {
            val status = secretStore.getStatus(currentUserId)
            val newSecret = if (!status.isEnrolled) TwoFactorAuthenticator.generateSecretBase32() else null
            val uri = newSecret?.let {
                TwoFactorAuthenticator.buildOtpAuthUri(
                    accountEmailOrLabel = userEmail ?: currentUserId,
                    secretBase32 = it,
                )
            }
            val remainingReauth = AuthManager.remainingFinancialReauthMillis()
            val authTime = if (remainingReauth > 0L) AuthManager.currentFirebaseAuthTimeSeconds() else null

            _twoFactorChallenge.value = TwoFactorChallengeUiState(
                pendingCheckout = pending,
                isSetupOnlyMode = false,
                accountEmail = userEmail ?: currentUserId,
                localTwoFactorStatus = status,
                enrollmentSecretBase32 = newSecret,
                otpAuthUri = uri,
                remainingReauthMillis = remainingReauth,
                firebaseAuthTimeSeconds = authTime,
                enrolledFirebaseFactorsCount = AuthManager.enrolledFirebaseMfaCount(),
            )
        }
    }

    private fun executeFreeActivation(businessName: String, cycle: BillingCycle) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.activateProviderSubscription(
                userId = currentUserId,
                businessName = businessName,
                tier = SubscriptionTier.FREE_STARTER,
                cycle = cycle,
                method = PaymentMethodType.NONE,
                autoRenew = false,
                verificationId = null,
            )
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = { invoice ->
                    MutationNotice("Starter Free activated (4 free features enabled). Receipt $invoice saved.")
                },
                onFailure = { error ->
                    MutationNotice(error.localizedMessage ?: "Failed to activate free plan.", isError = true)
                },
            )
        }
    }

    fun triggerFirebaseStepUpReauth(context: Context) {
        val current = _twoFactorChallenge.value ?: return
        if (current.isReauthenticating || current.isSubmitting) return

        viewModelScope.launch {
            _twoFactorChallenge.value = current.copy(isReauthenticating = true, errorMessage = null)
            val result = AuthManager.reauthenticateForFinancialAction(context)
            val latest = _twoFactorChallenge.value ?: return@launch
            when (result) {
                is FinancialStepUpResult.Verified -> {
                    _twoFactorChallenge.value = latest.copy(
                        isReauthenticating = false,
                        remainingReauthMillis = AuthManager.remainingFinancialReauthMillis(),
                        firebaseAuthTimeSeconds = result.firebaseAuthTimeSeconds,
                        enrolledFirebaseFactorsCount = result.enrolledFirebaseFactorsCount,
                        errorMessage = null,
                    )
                }
                FinancialStepUpResult.Cancelled -> {
                    _twoFactorChallenge.value = latest.copy(
                        isReauthenticating = false,
                        errorMessage = "Firebase Auth verification was cancelled. Complete step 1 to continue.",
                    )
                }
                FinancialStepUpResult.Timeout -> {
                    _twoFactorChallenge.value = latest.copy(
                        isReauthenticating = false,
                        errorMessage = "Firebase Auth verification timed out. Please try again.",
                    )
                }
                is FinancialStepUpResult.MultiFactorChallengeRequired -> {
                    _twoFactorChallenge.value = latest.copy(
                        isReauthenticating = false,
                        errorMessage = "Your Google account requires multi-factor confirmation. Retry Sign-In from Profile to complete the challenge.",
                    )
                }
                is FinancialStepUpResult.Error -> {
                    _twoFactorChallenge.value = latest.copy(
                        isReauthenticating = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun submitTwoFactorVerification(totpCode: String) {
        val challenge = _twoFactorChallenge.value ?: return
        if (challenge.isSubmitting || challenge.isReauthenticating) return

        viewModelScope.launch {
            // Verify Factor 1: Fresh Firebase Auth Re-authentication window
            val authTimeSec = if (AuthManager.remainingFinancialReauthMillis() > 0L) {
                challenge.firebaseAuthTimeSeconds ?: AuthManager.currentFirebaseAuthTimeSeconds()
            } else {
                null
            }
            if (authTimeSec == null || authTimeSec <= 0L) {
                _twoFactorChallenge.value = challenge.copy(
                    remainingReauthMillis = 0L,
                    errorMessage = "Factor 1 incomplete: Verify with Firebase Auth first before entering your 6-digit code.",
                )
                return@launch
            }

            // Check cooldown lockout
            val currentStatus = secretStore.getStatus(currentUserId)
            val cooldown = currentStatus.remainingCooldownSeconds()
            if (cooldown > 0 && challenge.enrollmentSecretBase32 == null) {
                _twoFactorChallenge.value = challenge.copy(
                    localTwoFactorStatus = currentStatus,
                    errorMessage = "Too many failed 2FA attempts. Wait ${cooldown}s before trying again.",
                )
                return@launch
            }

            _twoFactorChallenge.value = challenge.copy(isSubmitting = true, errorMessage = null)

            // Verify Factor 2: RFC-6238 TOTP (either inline setup or existing enrolled secret)
            val setupSecret = challenge.enrollmentSecretBase32
            val hasNativeMfa = challenge.enrolledFirebaseFactorsCount > 0
            if (setupSecret != null) {
                when (TwoFactorAuthenticator.verifyTotpCode(setupSecret, totpCode)) {
                    is TotpVerifyOutcome.Verified -> {
                        val fingerprint = secretStore.saveEnrolledSecret(currentUserId, setupSecret)
                        val enrollRes = repository.saveProviderSecurityEnrollment(
                            userId = currentUserId,
                            secretFingerprint = fingerprint,
                            hasNativeFirebaseMfa = hasNativeMfa,
                        )
                        if (enrollRes.isFailure) {
                            _twoFactorChallenge.value = challenge.copy(
                                isSubmitting = false,
                                errorMessage = enrollRes.exceptionOrNull()?.localizedMessage
                                    ?: "Failed to save 2FA enrollment metadata.",
                            )
                            return@launch
                        }
                    }
                    TotpVerifyOutcome.InvalidFormat -> {
                        _twoFactorChallenge.value = challenge.copy(
                            isSubmitting = false,
                            errorMessage = "Enter a valid 6-digit code from your Authenticator app.",
                        )
                        return@launch
                    }
                    TotpVerifyOutcome.Mismatch,
                    TotpVerifyOutcome.CodeAlreadyUsed -> {
                        _twoFactorChallenge.value = challenge.copy(
                            isSubmitting = false,
                            errorMessage = "6-digit code did not match your Authenticator key. Wait for the next code or verify key entry.",
                        )
                        return@launch
                    }
                }
            } else {
                val (outcome, updatedStatus) = secretStore.recordVerificationAttempt(currentUserId, totpCode)
                when (outcome) {
                    is TotpVerifyOutcome.Verified -> Unit
                    TotpVerifyOutcome.InvalidFormat -> {
                        _twoFactorChallenge.value = challenge.copy(
                            isSubmitting = false,
                            localTwoFactorStatus = updatedStatus,
                            errorMessage = "Enter a 6-digit numeric Authenticator code.",
                        )
                        return@launch
                    }
                    TotpVerifyOutcome.CodeAlreadyUsed -> {
                        _twoFactorChallenge.value = challenge.copy(
                            isSubmitting = false,
                            localTwoFactorStatus = updatedStatus,
                            errorMessage = "This 6-digit code was already used. Wait for the next 30-second code.",
                        )
                        return@launch
                    }
                    TotpVerifyOutcome.Mismatch, null -> {
                        val lockSecs = updatedStatus.remainingCooldownSeconds()
                        val msg = if (lockSecs > 0) {
                            "5 failed attempts reached. Locked for ${lockSecs}s."
                        } else {
                            val left = (TwoFactorAuthenticator.MAX_FAILED_ATTEMPTS - updatedStatus.failedAttempts).coerceAtLeast(1)
                            "Incorrect 6-digit 2FA code ($left attempt(s) left before 60s lockout)."
                        }
                        _twoFactorChallenge.value = challenge.copy(
                            isSubmitting = false,
                            localTwoFactorStatus = updatedStatus,
                            errorMessage = msg,
                        )
                        return@launch
                    }
                }
            }

            // If setup-only mode (launched from security banner without payment), we're done!
            val pending = challenge.pendingCheckout
            if (challenge.isSetupOnlyMode || pending == null) {
                _twoFactorChallenge.value = null
                _notice.value = MutationNotice("Two-Factor Authentication (2FA) is now enabled for financial transactions.")
                return@launch
            }

            // Record immutable Firebase Auth + 2FA audit proof in Firestore BEFORE paid ledger write
            val auditResult = repository.recordFinancialVerificationAudit(
                userId = currentUserId,
                firebaseAuthTimeSeconds = authTimeSec,
                hasNativeFirebaseMfa = hasNativeMfa,
                actionType = pending.isActionType,
            )
            if (auditResult.isFailure) {
                _twoFactorChallenge.value = challenge.copy(
                    isSubmitting = false,
                    errorMessage = auditResult.exceptionOrNull()?.localizedMessage
                        ?: "Could not record 2FA verification audit receipt.",
                )
                return@launch
            }
            val verificationId = auditResult.getOrThrow()

            // Execute paid subscription & billing payment linked to verificationId
            val payResult = repository.activateProviderSubscription(
                userId = currentUserId,
                businessName = pending.businessName,
                tier = pending.tier,
                cycle = pending.cycle,
                method = pending.method,
                autoRenew = pending.autoRenew,
                mobileNumber = pending.mobileNumber,
                cardholderName = pending.cardholderName,
                cardNumber = pending.cardNumber,
                cardExpiry = pending.cardExpiry,
                cardCvc = pending.cardCvc,
                paypalEmail = pending.paypalEmail,
                bankAccountRef = pending.bankAccountRef,
                verificationId = verificationId,
            )

            _twoFactorChallenge.value = null
            _notice.value = payResult.fold(
                onSuccess = { invoice ->
                    val statusHint = when (pending.method) {
                        PaymentMethodType.ECOCASH, PaymentMethodType.INNBUCKS ->
                            "2FA verified! Mobile prompt initiated for ${pending.method.displayName}. Receipt $invoice saved."
                        PaymentMethodType.BANK_EFT ->
                            "2FA verified! Bank transfer reference $invoice recorded (pending settlement)."
                        else ->
                            "2FA verified! ${pending.tier.title} (${pending.cycle.label}) activated via ${pending.method.displayName}. Invoice $invoice saved."
                    }
                    MutationNotice(statusHint, isError = false)
                },
                onFailure = { error ->
                    MutationNotice(error.localizedMessage ?: "Checkout failed after 2FA check.", isError = true)
                },
            )
        }
    }

    private companion object {
        const val TAG = "BillingViewModel"
    }
}
