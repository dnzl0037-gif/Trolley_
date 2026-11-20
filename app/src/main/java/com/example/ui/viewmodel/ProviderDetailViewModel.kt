package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.ProviderReview
import com.example.data.model.ReviewCalculator
import com.example.data.model.UiState
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

data class ProviderDetailSnapshot(
    val provider: ProviderProfile? = null,
    val reviews: List<ProviderReview> = emptyList(),
    val summary: ProviderRatingSummary = ProviderRatingSummary(),
    val ownReview: ProviderReview? = null,
    val relatedBookingId: String? = null,
    val isOwnProfile: Boolean = false,
)

class ProviderDetailViewModel(
    private val repository: TrolleyRepository,
    private val providerId: String,
    private val currentUserId: String,
    private val reviewerDisplayName: String,
) : ViewModel() {

    val uiState: StateFlow<UiState<ProviderDetailSnapshot>> = combine(
        repository.observeProviderProfileById(providerId),
        repository.observeProviderReviews(providerId),
        repository.observeCustomerBookings(currentUserId),
    ) { profile, reviews, myBookings ->
        val summary = ReviewCalculator.computeSummary(providerId, reviews)
        val ownReview = reviews.firstOrNull { it.customerId == currentUserId }
        val eligibleBookingId = myBookings
            .firstOrNull {
                it.providerId == providerId && (it.status == "completed" || it.status == "accepted")
            }?.id
        UiState.Success(
            ProviderDetailSnapshot(
                provider = profile,
                reviews = reviews,
                summary = summary,
                ownReview = ownReview,
                relatedBookingId = eligibleBookingId,
                isOwnProfile = profile?.userId == currentUserId,
            )
        ) as UiState<ProviderDetailSnapshot>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error loading provider detail for $providerId", rootError)
        emit(UiState.Error(rootError.message ?: "Unable to load provider profile and reviews."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _notice = MutableStateFlow<MutationNotice?>(null)
    val notice: StateFlow<MutationNotice?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    fun submitReview(
        provider: ProviderProfile,
        rating: Int,
        comment: String,
        relatedBookingId: String?,
        isUpdate: Boolean,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.submitProviderReview(
                provider = provider,
                reviewerName = reviewerDisplayName,
                rating = rating,
                comment = comment,
                bookingId = relatedBookingId,
            )
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = {
                    MutationNotice(
                        if (isUpdate) {
                            "Your review for ${provider.businessName} has been updated."
                        } else {
                            "Thank you! Your ${rating}-star review is now saved."
                        }
                    )
                },
                onFailure = { error ->
                    MutationNotice(
                        error.localizedMessage ?: "Unable to save your review.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun deleteMyReview() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.deleteProviderReview(providerId)
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = { MutationNotice("Your review has been removed.") },
                onFailure = { error ->
                    MutationNotice(
                        error.localizedMessage ?: "Unable to delete review.",
                        isError = true,
                    )
                },
            )
        }
    }

    private companion object {
        const val TAG = "ProviderDetailVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
