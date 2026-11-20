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

@Keep
@IgnoreExtraProperties
data class ProviderSubscription(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val businessName: String = "",
    val planTier: String = "free_starter",
    val billingCycle: String = "monthly",
    val paymentMethod: String = "none",
    val currency: String = "USD",
    val amountCents: Int = 0,
    val monthlyBookingLimit: Int = 3,
    val maxCategories: Int = 1,
    val subscriptionStatus: String = "active",
    val autoRenew: Boolean = false,
    val maskedInstrument: String? = null,
    val verificationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BillingPayment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val planTier: String = "free_starter",
    val billingCycle: String = "monthly",
    val paymentMethod: String = "none",
    val currency: String = "USD",
    val subtotalCents: Int = 0,
    val discountCents: Int = 0,
    val amountCents: Int = 0,
    val maskedInstrument: String = "",
    val invoiceNumber: String = "",
    val paymentStatus: String = "free_activated",
    val verificationId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ProviderSecurityConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val twoFactorEnabled: Boolean = false,
    val twoFactorType: String = "totp_authenticator",
    val secretFingerprint: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FinancialVerification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val verificationMethod: String = "firebase_reauth_and_totp",
    val reauthenticated: Boolean = false,
    val totpVerified: Boolean = false,
    val firebaseAuthTimeSeconds: Long = 0L,
    val actionType: String = "activate_subscription",
    @ServerTimestamp val verifiedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val customerId: String = "",
    val providerId: String = "",
    val providerUserId: String = "",
    val participantIds: List<String> = emptyList(),
    val customerName: String = "",
    val providerBusinessName: String = "",
    val providerCategoryName: String = "",
    val lastMessageText: String = "",
    val lastSenderId: String = "",
    val messageCount: Int = 0,
    @ServerTimestamp val lastCustomerReadAt: Timestamp? = null,
    @ServerTimestamp val lastProviderReadAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun hasUnreadFor(currentUserId: String): Boolean {
        if (lastSenderId.isBlank() || lastSenderId == currentUserId) return false
        val updatedMs = updatedAt?.toDate()?.time ?: return false
        val readAt = if (currentUserId == customerId) lastCustomerReadAt else lastProviderReadAt
        val readMs = readAt?.toDate()?.time ?: 0L
        return updatedMs > readMs
    }

    fun partnerTitleFor(currentUserId: String): String {
        return if (currentUserId == customerId) providerBusinessName else customerName
    }
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val threadId: String = "",
    val providerId: String = "",
    val providerUserId: String = "",
    val customerId: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val senderName: String = "",
    val body: String = "",
    val messageType: String = "text",
    val clientSentMillis: Long = 0L,
    @ServerTimestamp val sentAt: Timestamp? = null,
)

object ChatValidator {
    const val MAX_MESSAGE_LENGTH = 1000
    val ALLOWED_MESSAGE_TYPES = setOf("text", "pre_booking_inquiry", "quote_update")

    val PRE_BOOKING_PROMPTS = listOf(
        "Are you available this week?",
        "What is your estimated quote for this job?",
        "Do you service my neighbourhood?",
        "Can we discuss tools and materials before I book?",
    )

    fun deterministicThreadId(providerId: String, customerId: String): String =
        "${providerId.trim()}__${customerId.trim()}"

    fun isValidMessageBody(body: String): Boolean =
        body.trim().length in 1..MAX_MESSAGE_LENGTH

    fun resolveMessageType(rawType: String): String {
        val clean = rawType.trim()
        return if (clean in ALLOWED_MESSAGE_TYPES) clean else "text"
    }

    fun buildParticipantIds(customerId: String, providerUserId: String): List<String> =
        listOf(customerId.trim(), providerUserId.trim())

    fun resolveSenderRole(rawRole: String): String {
        val clean = rawRole.trim()
        return if (clean in ALLOWED_COMMUNITY_ROLES) clean else "member"
    }

    val ALLOWED_COMMUNITY_ROLES = setOf("member", "provider")
}

@Keep
@IgnoreExtraProperties
data class CommunityMessage(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "member",
    val body: String = "",
    val clientSentMillis: Long = 0L,
    @ServerTimestamp val sentAt: Timestamp? = null,
)

object ChatCooldownTracker {
    const val SEND_COOLDOWN_MS = 3_000L
    private val lastSentByUser = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun remainingCooldownMs(
        userId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        if (userId.isBlank()) return 0L
        val last = lastSentByUser[userId] ?: return 0L
        val elapsed = (nowMs - last).coerceAtLeast(0L)
        return (SEND_COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    fun remainingCooldownSeconds(
        userId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        val rem = remainingCooldownMs(userId, nowMs)
        if (rem <= 0L) return 0
        return ((rem + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    fun recordSent(
        userId: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (userId.isNotBlank()) {
            lastSentByUser[userId] = nowMs
        }
    }

    fun clearForTests() {
        lastSentByUser.clear()
    }
}

object IdempotencyKeys {
    private val sanitized = Regex("[^a-zA-Z0-9_\\-]")

    fun sanitizeId(raw: String, maxLen: Int = 96): String =
        raw.trim().replace(sanitized, "_").take(maxLen).ifBlank { "id_${System.currentTimeMillis()}" }

    fun billingPaymentDocId(userId: String, checkoutNonce: String): String =
        sanitizeId("pay_${userId.take(24)}_${checkoutNonce.take(36)}")
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
