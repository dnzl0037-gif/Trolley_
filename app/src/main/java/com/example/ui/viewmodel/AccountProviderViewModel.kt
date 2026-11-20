package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.AuthManager
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.ProviderReview
import com.example.data.model.ProviderSubscription
import com.example.data.model.ReviewCalculator
import com.example.data.model.UiState
import com.example.data.model.UserAccount
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MutationNotice(
    val message: String,
    val isError: Boolean = false,
)

class AccountProviderViewModel(
    private val repository: TrolleyRepository,
    private val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String?,
    private val initialPhotoUrl: String? = null,
) : ViewModel() {

    val userAccountState: StateFlow<UiState<UserAccount?>> = repository.observeUserAccount(currentUserId)
        .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user account", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load account profile."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val providerProfileState: StateFlow<UiState<ProviderProfile?>> = repository.observeProviderProfile(currentUserId)
        .map<ProviderProfile?, UiState<ProviderProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing provider profile", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load your saved provider profile. Please retry."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val ownReviewsState: StateFlow<UiState<List<ProviderReview>>> = repository.observeProviderReviews(currentUserId)
        .map<List<ProviderReview>, UiState<List<ProviderReview>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing own reviews", rootError)
            emit(UiState.Error(rootError.message ?: "Unable to load customer reviews."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val ownRatingSummary: StateFlow<ProviderRatingSummary> = ownReviewsState
        .map { state ->
            val list = (state as? UiState.Success)?.data.orEmpty()
            ReviewCalculator.computeSummary(currentUserId, list)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProviderRatingSummary(providerId = currentUserId),
        )

    val ownSubscriptionState: StateFlow<ProviderSubscription?> = repository.observeProviderSubscription(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null,
        )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _notice = MutableStateFlow<MutationNotice?>(null)
    val notice: StateFlow<MutationNotice?> = _notice.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserAccount(
                userId = currentUserId,
                fullName = initialDisplayName,
                email = initialEmail,
                photoUrl = initialPhotoUrl,
            )
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun updateUserProfile(
        displayName: String,
        httpsPhotoUrl: String?,
        photoBase64: String?,
        clearAuthPhoto: Boolean,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null

            val authResult = AuthManager.updateAuthProfile(
                displayName = displayName,
                httpsPhotoUrl = httpsPhotoUrl,
                clearAuthPhoto = clearAuthPhoto,
            )
            if (authResult.isFailure) {
                _isBusy.value = false
                _notice.value = MutationNotice(
                    message = authResult.exceptionOrNull()?.localizedMessage ?: "Unable to update Firebase Auth profile.",
                    isError = true,
                )
                return@launch
            }

            val repoResult = repository.updateUserProfile(
                userId = currentUserId,
                fullName = displayName,
                email = initialEmail,
                photoUrl = httpsPhotoUrl?.trim()?.ifBlank { null },
                photoBase64 = photoBase64?.trim()?.ifBlank { null },
            )
            _isBusy.value = false
            _notice.value = repoResult.fold(
                onSuccess = { MutationNotice("Profile updated across your Firebase Auth session and account.") },
                onFailure = { error ->
                    MutationNotice(error.localizedMessage ?: "Saved to Auth, but failed to sync account record.", isError = true)
                },
            )
        }
    }

    fun verifyAge(fullName: String, dateOfBirthIso: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.saveAgeVerification(
                userId = currentUserId,
                fullName = fullName,
                email = initialEmail,
                dateOfBirthIso = dateOfBirthIso,
            )
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = { MutationNotice("Account age requirement confirmed (13+).") },
                onFailure = { MutationNotice(it.localizedMessage ?: "Enter a valid date of birth.", isError = true) },
            )
        }
    }

    fun createProviderDraft(
        businessName: String,
        description: String,
        categorySlug: String,
        countryCode: String,
        city: String,
        businessAddress: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        onCreated: () -> Unit = {},
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.createProviderDraft(
                userId = currentUserId,
                businessName = businessName,
                description = description,
                categorySlug = categorySlug,
                countryCode = countryCode,
                city = city,
                businessAddress = businessAddress,
                latitude = latitude,
                longitude = longitude,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = { ref ->
                    _notice.value = MutationNotice("Provider profile saved (ref ${ref.take(8)}). Choose your Free or Pro subscription plan.")
                    onCreated()
                },
                onFailure = { error ->
                    _notice.value = MutationNotice(error.localizedMessage ?: "Unable to save your provider draft. Please try again.", isError = true)
                },
            )
        }
    }

    fun updateProviderDraft(
        currentStatus: String,
        businessName: String,
        description: String,
        categorySlug: String,
        countryCode: String,
        city: String,
        businessAddress: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
        submitForReview: Boolean,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.updateProviderDraft(
                userId = currentUserId,
                currentStatus = currentStatus,
                businessName = businessName,
                description = description,
                categorySlug = categorySlug,
                countryCode = countryCode,
                city = city,
                businessAddress = businessAddress,
                latitude = latitude,
                longitude = longitude,
                submitForReview = submitForReview,
            )
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = {
                    if (submitForReview) {
                        MutationNotice("Listing submitted for review. An administrator must review and approve it before customers can find it.")
                    } else {
                        MutationNotice("Changes saved. Your stored details and review status are up to date.")
                    }
                },
                onFailure = { error ->
                    MutationNotice(error.localizedMessage ?: "Unable to confirm this save. Reload your saved details before retrying.", isError = true)
                },
            )
        }
    }

    fun reloadSavedProfile() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.getProviderProfileOnce(currentUserId)
            _isBusy.value = false
            result.fold(
                onSuccess = { profile ->
                    _notice.value = if (profile != null) {
                        MutationNotice("Reloaded stored profile (${profile.listingStatus.replace('_', ' ')}).")
                    } else {
                        MutationNotice("No saved provider draft found yet.")
                    }
                },
                onFailure = { error ->
                    _notice.value = MutationNotice(error.localizedMessage ?: "Unable to reload saved details.", isError = true)
                },
            )
        }
    }

    private companion object {
        const val TAG = "AccountProviderVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
