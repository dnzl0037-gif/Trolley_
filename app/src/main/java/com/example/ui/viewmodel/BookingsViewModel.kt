package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.BookingRequest
import com.example.data.model.ProviderProfile
import com.example.data.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookingsViewModel(
    private val repository: TrolleyRepository,
    private val currentUserId: String,
    private val customerDisplayName: String,
) : ViewModel() {

    val customerBookingsState: StateFlow<UiState<List<BookingRequest>>> =
        repository.observeCustomerBookings(currentUserId)
            .map<List<BookingRequest>, UiState<List<BookingRequest>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing customer bookings", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load your booking requests."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val providerBookingsState: StateFlow<UiState<List<BookingRequest>>> =
        repository.observeProviderBookings(currentUserId)
            .map<List<BookingRequest>, UiState<List<BookingRequest>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing provider bookings", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load incoming client bookings."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    val bookableProvidersState: StateFlow<UiState<List<ProviderProfile>>> =
        combine(
            repository.observePublishedProviders(),
            repository.observeProviderProfile(currentUserId),
        ) { published, own ->
            val publishedIds = published.map { it.id }.toSet()
            val ownList = listOfNotNull(own?.takeIf { it.id !in publishedIds })
            UiState.Success(published + ownList) as UiState<List<ProviderProfile>>
        }.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing bookable providers", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load available providers."))
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

    fun submitBookingRequest(
        provider: ProviderProfile,
        scheduledDate: String,
        scheduledTime: String,
        notes: String,
        onBooked: () -> Unit = {},
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.createBookingRequest(
                provider = provider,
                customerName = customerDisplayName,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                notes = notes,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _notice.value = MutationNotice(
                        "Booking request sent to ${provider.businessName} for $scheduledDate at $scheduledTime."
                    )
                    onBooked()
                },
                onFailure = { error ->
                    _notice.value = MutationNotice(
                        error.localizedMessage ?: "Unable to save your booking request.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun rescheduleBooking(
        booking: BookingRequest,
        scheduledDate: String,
        scheduledTime: String,
        notes: String,
        onRescheduled: () -> Unit = {},
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.rescheduleBookingRequest(
                bookingId = booking.id,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                notes = notes,
            )
            _isBusy.value = false
            result.fold(
                onSuccess = {
                    _notice.value = MutationNotice(
                        "Rescheduled ${booking.providerBusinessName} to $scheduledDate at $scheduledTime."
                    )
                    onRescheduled()
                },
                onFailure = { error ->
                    _notice.value = MutationNotice(
                        error.localizedMessage ?: "Unable to reschedule booking.",
                        isError = true,
                    )
                },
            )
        }
    }

    fun updateStatus(
        booking: BookingRequest,
        nextStatus: String,
        asProvider: Boolean,
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _notice.value = null
            val result = repository.updateBookingStatus(
                booking = booking,
                nextStatus = nextStatus,
                asProvider = asProvider,
            )
            _isBusy.value = false
            _notice.value = result.fold(
                onSuccess = {
                    MutationNotice("Booking marked as $nextStatus.")
                },
                onFailure = { error ->
                    MutationNotice(
                        error.localizedMessage ?: "Unable to update booking status.",
                        isError = true,
                    )
                },
            )
        }
    }

    private companion object {
        const val TAG = "BookingsVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
