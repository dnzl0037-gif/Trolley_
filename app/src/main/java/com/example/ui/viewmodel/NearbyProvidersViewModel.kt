package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.TrolleyRepository
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class NearbyProvidersSnapshot(
    val publishedProviders: List<ProviderProfile> = emptyList(),
    val ownProfile: ProviderProfile? = null,
    val ratingSummaries: Map<String, ProviderRatingSummary> = emptyMap(),
)

class NearbyProvidersViewModel(
    repository: TrolleyRepository,
    currentUserId: String,
) : ViewModel() {

    val providersState: StateFlow<UiState<NearbyProvidersSnapshot>> = combine(
        repository.observePublishedProviders(),
        repository.observeProviderProfile(currentUserId),
        repository.observeAllProviderRatings(),
    ) { published, own, ratings ->
        UiState.Success(
            NearbyProvidersSnapshot(
                publishedProviders = published,
                ownProfile = own,
                ratingSummaries = ratings,
            )
        ) as UiState<NearbyProvidersSnapshot>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing nearby provider profiles", rootError)
        emit(UiState.Error(rootError.message ?: "Unable to load nearby service providers."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
    )

    private companion object {
        const val TAG = "NearbyProvidersVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
