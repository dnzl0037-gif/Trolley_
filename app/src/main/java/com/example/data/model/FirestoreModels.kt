package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String? = null,
    val dateOfBirth: String? = null,
    val ageVerified: Boolean = false,
    val photoUrl: String? = null,
    val photoBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ProviderProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessName: String = "",
    val description: String? = null,
    val categorySlug: String = "",
    val categoryName: String = "",
    val countryCode: String = "ZW",
    val city: String = "",
    val businessAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val listingStatus: String = "draft",
    val verificationStatus: String = "not_submitted",
    @ServerTimestamp val submittedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val isEditable: Boolean
        get() = listingStatus == "draft" || listingStatus == "pending_review"

    val formattedLocation: String
        get() = listOfNotNull(
            businessAddress?.takeIf { it.isNotBlank() },
            city.takeIf { it.isNotBlank() },
            countryCode.takeIf { it.isNotBlank() },
        ).joinToString(", ")
}

data class MapProviderMarker(
    val profile: ProviderProfile,
    val familyName: String,
    val latitude: Double,
    val longitude: Double,
    val isExactAddress: Boolean,
    val isOwnPreview: Boolean,
    val distanceKm: Double? = null,
)

@Keep
@IgnoreExtraProperties
data class BookingRequest(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val providerId: String = "",
    val providerUserId: String = "",
    val providerBusinessName: String = "",
    val categorySlug: String = "",
    val categoryName: String = "",
    val providerLocation: String = "",
    val scheduledDate: String = "",
    val scheduledTime: String = "",
    val notes: String? = null,
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val canCustomerCancel: Boolean
        get() = status == "pending" || status == "accepted"

    val canCustomerReschedule: Boolean
        get() = status == "pending"

    val canProviderRespond: Boolean
        get() = status == "pending"

    val canProviderComplete: Boolean
        get() = status == "accepted"
}

object BookingValidator {
    private val DATE_REGEX = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
    private val TIME_REGEX = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

    fun isValidScheduledDate(dateIso: String, today: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        val trimmed = dateIso.trim()
        if (!DATE_REGEX.matches(trimmed)) return false
        val parsed = runCatching { java.time.LocalDate.parse(trimmed) }.getOrNull() ?: return false
        return !parsed.isBefore(today)
    }

    fun isValidScheduledTime(time24h: String): Boolean {
        return TIME_REGEX.matches(time24h.trim())
    }

    fun isAllowedStatusTransition(currentStatus: String, nextStatus: String, asProvider: Boolean): Boolean {
        return if (asProvider) {
            (currentStatus == "pending" && (nextStatus == "accepted" || nextStatus == "declined")) ||
                (currentStatus == "accepted" && nextStatus == "completed")
        } else {
            (currentStatus == "pending" || currentStatus == "accepted") && nextStatus == "cancelled"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class ProviderReview(
    @DocumentId val id: String = "",
    val providerId: String = "",
    val providerUserId: String = "",
    val customerId: String = "",
    val reviewerName: String = "",
    val rating: Int = 5,
    val comment: String = "",
    val bookingId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class ProviderRatingSummary(
    val providerId: String = "",
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val ratingCounts: Map<Int, Int> = emptyMap(),
) {
    val formattedAverage: String
        get() = ReviewCalculator.formatAverage(averageRating)
}

object ReviewCalculator {
    fun deterministicReviewId(providerId: String, customerId: String): String =
        "${providerId}__${customerId}"

    fun isValidRating(rating: Int): Boolean = rating in 1..5

    fun isValidComment(comment: String): Boolean = comment.trim().length in 2..1000

    fun computeSummary(providerId: String, reviews: List<ProviderReview>): ProviderRatingSummary {
        val valid = reviews.filter { it.rating in 1..5 }
        if (valid.isEmpty()) {
            return ProviderRatingSummary(providerId = providerId)
        }
        val avg = valid.sumOf { it.rating }.toDouble() / valid.size.toDouble()
        val counts = (1..5).associateWith { stars -> valid.count { it.rating == stars } }
        return ProviderRatingSummary(
            providerId = providerId,
            averageRating = avg,
            reviewCount = valid.size,
            ratingCounts = counts,
        )
    }

    fun computeAllSummaries(reviews: List<ProviderReview>): Map<String, ProviderRatingSummary> {
        return reviews
            .filter { it.providerId.isNotBlank() && it.rating in 1..5 }
            .groupBy { it.providerId }
            .mapValues { (providerId, group) -> computeSummary(providerId, group) }
    }

    fun formatAverage(averageRating: Double): String {
        return if (averageRating <= 0.0) {
            "0.0"
        } else {
            String.format(java.util.Locale.ENGLISH, "%.1f", averageRating)
        }
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
