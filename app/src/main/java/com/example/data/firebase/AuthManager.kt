package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface GoogleSignInResult {
    data class Success(
        val isNewUser: Boolean,
        val userId: String,
        val displayName: String,
        val email: String?,
        val photoUrl: String?,
    ) : GoogleSignInResult

    data object Cancelled : GoogleSignInResult
    data object Timeout : GoogleSignInResult
    data class Error(val message: String) : GoogleSignInResult
}

sealed interface FinancialStepUpResult {
    data class Verified(
        val userId: String,
        val firebaseAuthTimeSeconds: Long,
        val enrolledFirebaseFactorsCount: Int,
    ) : FinancialStepUpResult

    data class MultiFactorChallengeRequired(
        val resolver: MultiFactorResolver,
    ) : FinancialStepUpResult

    data object Cancelled : FinancialStepUpResult
    data object Timeout : FinancialStepUpResult
    data class Error(val message: String) : FinancialStepUpResult
}

object AuthManager {

    private const val TAG = "Auth"
    const val LOGIN_TIMEOUT_MS = 45_000L
    const val AUTO_LOGIN_TIMEOUT_MS = 12_000L
    const val VERIFICATION_WINDOW_MS = 10 * 60 * 1000L // 10 minutes
    const val FINANCIAL_REAUTH_WINDOW_MS = 5 * 60 * 1000L // 5 minutes for financial 2FA step-up
    const val MAX_FAILED_ATTEMPTS = 5
    const val COOLDOWN_SECONDS = 60

    private val _profileRevision = MutableStateFlow(0L)
    val profileRevision: StateFlow<Long> = _profileRevision.asStateFlow()

    @Volatile
    private var lastVerifiedFinancialReauthMs: Long = 0L

    fun normalizeEpochSeconds(rawTimestamp: Long): Long {
        if (rawTimestamp <= 0L) return 0L
        return if (rawTimestamp >= 10_000_000_000L) rawTimestamp / 1000L else rawTimestamp
    }

    fun remainingFinancialReauthMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        if (Firebase.auth.currentUser == null || lastVerifiedFinancialReauthMs <= 0L) return 0L
        val elapsed = (nowMillis - lastVerifiedFinancialReauthMs).coerceAtLeast(0L)
        return (FINANCIAL_REAUTH_WINDOW_MS - elapsed).coerceAtLeast(0L)
    }

    fun enrolledFirebaseMfaCount(): Int {
        val user = Firebase.auth.currentUser ?: return 0
        return runCatching { user.multiFactor.enrolledFactors.size }.getOrDefault(0)
    }

    tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun isValidWebClientId(clientId: String?): Boolean {
        val trimmed = clientId?.trim().orEmpty()
        return trimmed.isNotEmpty() && trimmed.endsWith(".apps.googleusercontent.com")
    }

    fun computeRemainingVerificationMillis(
        lastSignInTimestampMs: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        if (lastSignInTimestampMs <= 0L) return VERIFICATION_WINDOW_MS
        val elapsed = (nowMillis - lastSignInTimestampMs).coerceAtLeast(0L)
        return (VERIFICATION_WINDOW_MS - elapsed).coerceAtLeast(0L)
    }

    fun mapGoogleCredentialException(error: Throwable): GoogleSignInResult = when (error) {
        is TimeoutCancellationException -> GoogleSignInResult.Timeout
        is GetCredentialCancellationException -> GoogleSignInResult.Cancelled
        is NoCredentialException -> GoogleSignInResult.Error(
            "No Google account was found on this device. Add or sign in to a Google account in device Settings and try again."
        )
        is GetCredentialInterruptedException -> GoogleSignInResult.Error(
            "Google Sign-In was interrupted before finishing. Please try again."
        )
        is GetCredentialProviderConfigurationException,
        is GetCredentialUnsupportedException -> GoogleSignInResult.Error(
            "Google Play Services Credential Manager is unavailable or needs an update on this device."
        )
        is GoogleIdTokenParsingException -> GoogleSignInResult.Error(
            "Could not verify the Google ID token returned by this device. Please try again."
        )
        is FirebaseNetworkException -> GoogleSignInResult.Error(
            "Network error while contacting Google & Firebase. Check your connection and retry."
        )
        is FirebaseAuthException -> GoogleSignInResult.Error(
            error.localizedMessage ?: "Firebase authentication failed (${error.errorCode})."
        )
        else -> GoogleSignInResult.Error(
            error.localizedMessage ?: "Google sign-in failed. Please try again."
        )
    }

    suspend fun updateAuthProfile(
        displayName: String,
        httpsPhotoUrl: String? = null,
        clearAuthPhoto: Boolean = false,
    ): Result<Unit> {
        val user = Firebase.auth.currentUser
            ?: return Result.failure(IllegalStateException("Sign in first before updating your profile."))
        val cleanName = displayName.trim()
        if (cleanName.isEmpty() || cleanName.length > 120) {
            return Result.failure(IllegalArgumentException("Display name must be 1 to 120 characters."))
        }
        val cleanPhotoUrl = httpsPhotoUrl?.trim()?.ifBlank { null }
        if (cleanPhotoUrl != null && (!cleanPhotoUrl.startsWith("https://") || cleanPhotoUrl.length > 1000)) {
            return Result.failure(IllegalArgumentException("Photo URL must start with https:// and be under 1000 characters."))
        }

        return try {
            val request = userProfileChangeRequest {
                this.displayName = cleanName
                if (cleanPhotoUrl != null) {
                    this.photoUri = Uri.parse(cleanPhotoUrl)
                } else if (clearAuthPhoto) {
                    this.photoUri = null
                }
            }
            user.updateProfile(request).await()
            runCatching { user.reload().await() }
            _profileRevision.value += 1L
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firebase Auth profile", e)
            Result.failure(e)
        }
    }

    fun remainingFreshVerificationMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val user = Firebase.auth.currentUser ?: return 0L
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        return computeRemainingVerificationMillis(lastSignIn, nowMillis)
    }

    fun isSessionFreshForSensitiveActions(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return Firebase.auth.currentUser != null && remainingFreshVerificationMillis(nowMillis) > 0L
    }

    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (Firebase.auth.currentUser != null) return true
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (!isValidWebClientId(clientId)) return false
        val activityOrContext = context.findActivity() ?: context
        val credentialManager = CredentialManager.create(activityOrContext)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId!!)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            withTimeout(AUTO_LOGIN_TIMEOUT_MS) {
                val credential = credentialManager.getCredential(activityOrContext, request).credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    true
                } else {
                    false
                }
            }
        } catch (_: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun signInInteractive(context: Context): GoogleSignInResult {
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (!isValidWebClientId(clientId)) {
            return GoogleSignInResult.Error("Google Sign-In Web Client ID configuration is missing.")
        }
        val activity = context.findActivity()
            ?: return GoogleSignInResult.Error("Unable to launch Google Sign-In UI from this context.")

        val credentialManager = CredentialManager.create(activity)
        val explicitSignInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId!!).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(explicitSignInOption)
            .build()

        return try {
            withTimeout(LOGIN_TIMEOUT_MS) {
                val credential = try {
                    credentialManager.getCredential(activity, primaryRequest).credential
                } catch (primaryError: GetCredentialException) {
                    // Fallback to GetGoogleIdOption (supports both new sign-ups and existing accounts)
                    if (primaryError is NoCredentialException ||
                        primaryError is GetCredentialProviderConfigurationException ||
                        primaryError is GetCredentialUnsupportedException
                    ) {
                        Log.w(TAG, "Primary SignInWithGoogle option unavailable (${primaryError.javaClass.simpleName}); falling back to GetGoogleIdOption")
                        val fallbackOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val fallbackRequest = GetCredentialRequest.Builder()
                            .addCredentialOption(fallbackOption)
                            .build()
                        credentialManager.getCredential(activity, fallbackRequest).credential
                    } else {
                        throw primaryError
                    }
                }

                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val authResult = Firebase.auth
                        .signInWithCredential(GoogleAuthProvider.getCredential(googleCredential.idToken, null))
                        .await()
                    val firebaseUser = authResult.user
                        ?: return@withTimeout GoogleSignInResult.Error("Firebase returned an empty user session.")
                    val resolvedName = firebaseUser.displayName
                        ?.takeIf { it.isNotBlank() }
                        ?: googleCredential.displayName
                        ?: "Trolley Member"
                    val isNew = authResult.additionalUserInfo?.isNewUser == true
                    val photoUrl = firebaseUser.photoUrl?.toString()
                        ?: googleCredential.profilePictureUri?.toString()

                    // Ensure Firestore /users/{uid} record exists immediately on both Sign Up and Sign In
                    runCatching {
                        TrolleyRepository(activity.applicationContext).ensureUserAccount(
                            userId = firebaseUser.uid,
                            fullName = resolvedName,
                            email = firebaseUser.email,
                            photoUrl = photoUrl,
                        )
                    }

                    lastVerifiedFinancialReauthMs = System.currentTimeMillis()
                    GoogleSignInResult.Success(
                        isNewUser = isNew,
                        userId = firebaseUser.uid,
                        displayName = resolvedName,
                        email = firebaseUser.email,
                        photoUrl = photoUrl,
                    )
                } else {
                    GoogleSignInResult.Error("Unexpected credential type returned by Credential Manager.")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Google Sign-In timed out after ${LOGIN_TIMEOUT_MS}ms", e)
            GoogleSignInResult.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val mapped = mapGoogleCredentialException(e)
            if (mapped is GoogleSignInResult.Cancelled) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}")
            } else {
                Log.e(TAG, "Google Sign-In/Sign-Up failed", e)
            }
            mapped
        }
    }

    /**
     * Step 1 of Provider 2FA for Financial Transactions:
     * Performs a live Google Credential Manager challenge and executes
     * FirebaseUser.reauthenticate(GoogleAuthProvider credential) followed by a forced
     * ID-token refresh to capture a fresh cryptographic Firebase Auth auth_time claim.
     */
    suspend fun reauthenticateForFinancialAction(context: Context): FinancialStepUpResult {
        val currentUser = Firebase.auth.currentUser
            ?: return FinancialStepUpResult.Error("Sign in with Google first before authorizing financial changes.")
        val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrNull()
        if (!isValidWebClientId(clientId)) {
            return FinancialStepUpResult.Error("Google Sign-In Web Client ID configuration is missing.")
        }
        val activity = context.findActivity()
            ?: return FinancialStepUpResult.Error("Unable to launch Firebase re-authentication from this context.")

        val credentialManager = CredentialManager.create(activity)
        val explicitOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId!!).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(explicitOption)
            .build()

        return try {
            withTimeout(LOGIN_TIMEOUT_MS) {
                val credential = credentialManager.getCredential(activity, request).credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                    currentUser.reauthenticate(firebaseCred).await()
                    val tokenResult = currentUser.getIdToken(true).await()
                    val authTimeSec = normalizeEpochSeconds(tokenResult.authTimestamp)
                        .takeIf { it > 0L }
                        ?: (System.currentTimeMillis() / 1000L)
                    lastVerifiedFinancialReauthMs = System.currentTimeMillis()
                    val factorCount = runCatching { currentUser.multiFactor.enrolledFactors.size }.getOrDefault(0)
                    FinancialStepUpResult.Verified(
                        userId = currentUser.uid,
                        firebaseAuthTimeSeconds = authTimeSec,
                        enrolledFirebaseFactorsCount = factorCount,
                    )
                } else {
                    FinancialStepUpResult.Error("Unexpected credential returned during Firebase re-authentication.")
                }
            }
        } catch (mfaEx: FirebaseAuthMultiFactorException) {
            Log.i(TAG, "Firebase native MultiFactor challenge required during re-auth")
            FinancialStepUpResult.MultiFactorChallengeRequired(mfaEx.resolver)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Firebase re-authentication timed out after ${LOGIN_TIMEOUT_MS}ms", e)
            FinancialStepUpResult.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            when (val mapped = mapGoogleCredentialException(e)) {
                is GoogleSignInResult.Cancelled -> FinancialStepUpResult.Cancelled
                is GoogleSignInResult.Timeout -> FinancialStepUpResult.Timeout
                is GoogleSignInResult.Error -> FinancialStepUpResult.Error(mapped.message)
                is GoogleSignInResult.Success -> FinancialStepUpResult.Error("Unexpected sign-in state.")
            }
        }
    }

    suspend fun currentFirebaseAuthTimeSeconds(): Long? {
        val user = Firebase.auth.currentUser ?: return null
        val refreshed = runCatching { user.getIdToken(false).await() }.getOrNull()
        val tokenSec = normalizeEpochSeconds(refreshed?.authTimestamp ?: 0L)
        if (tokenSec > 0L) return tokenSec
        return normalizeEpochSeconds(user.metadata?.lastSignInTimestamp ?: 0L).takeIf { it > 0L }
    }

    suspend fun signOut(context: Context) {
        lastVerifiedFinancialReauthMs = 0L
        val credentialManager = CredentialManager.create(context.applicationContext)
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
