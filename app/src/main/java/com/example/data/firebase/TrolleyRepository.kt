package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.catalog.BillingCatalog
import com.example.data.catalog.BillingCycle
import com.example.data.catalog.PaymentMethodType
import com.example.data.catalog.PaymentValidator
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.catalog.SubscriptionTier
import com.example.data.model.BillingPayment
import com.example.data.model.BookingRequest
import com.example.data.model.BookingValidator
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ChatValidator
import com.example.data.model.FinancialVerification
import com.example.data.model.ProviderProfile
import com.example.data.model.ProviderRatingSummary
import com.example.data.model.ProviderReview
import com.example.data.model.ProviderSecurityConfig
import com.example.data.model.ProviderSubscription
import com.example.data.model.ReviewCalculator
import com.example.data.model.UserAccount
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.Period
import java.util.Locale

class TrolleyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserAccount(
        userId: String,
        fullName: String,
        email: String?,
        photoUrl: String? = null,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val safeName = fullName.trim().ifBlank { "Trolley Member" }.take(120)
                val safePhotoUrl = photoUrl?.trim()?.takeIf { it.startsWith("https://") && it.length <= 1000 }
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("fullName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.trim().take(200))
                    if (safePhotoUrl != null) put("photoUrl", safePhotoUrl)
                    put("ageVerified", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        userId: String,
        fullName: String,
        email: String?,
        photoUrl: String?,
        photoBase64: String?,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val safeName = fullName.trim()
            require(safeName.length in 1..120) { "Display name must be 1 to 120 characters." }

            val cleanPhotoUrl = photoUrl?.trim()?.ifBlank { null }
            if (cleanPhotoUrl != null) {
                require(cleanPhotoUrl.startsWith("https://") && cleanPhotoUrl.length <= 1000) {
                    "Photo URL must start with https:// and be under 1000 characters."
                }
            }
            val cleanPhotoBase64 = photoBase64?.trim()?.ifBlank { null }
            if (cleanPhotoBase64 != null) {
                require(cleanPhotoBase64.length <= 80_000) {
                    "Selected image is too large. Please pick another image."
                }
            }

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "fullName" to safeName,
                    "photoUrl" to (cleanPhotoUrl ?: FieldValue.delete()),
                    "photoBase64" to (cleanPhotoBase64 ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!email.isNullOrBlank()) {
                    updates["email"] = email.trim().take(200)
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("fullName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.trim().take(200))
                    put("ageVerified", false)
                    if (cleanPhotoUrl != null) put("photoUrl", cleanPhotoUrl)
                    if (cleanPhotoBase64 != null) put("photoBase64", cleanPhotoBase64)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveAgeVerification(
        userId: String,
        fullName: String,
        email: String?,
        dateOfBirthIso: String,
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val trimmedDob = dateOfBirthIso.trim()
            val parsedDob = runCatching { LocalDate.parse(trimmedDob) }.getOrNull()
                ?: throw IllegalArgumentException("Enter a valid date of birth (YYYY-MM-DD).")
            val today = LocalDate.now()
            if (parsedDob.isAfter(today)) {
                throw IllegalArgumentException("Date of birth cannot be in the future.")
            }
            val years = Period.between(parsedDob, today).years
            if (years < 13) {
                throw IllegalArgumentException("You must be at least 13 years old. Under-13 data is not saved.")
            }

            val safeName = fullName.trim().ifBlank { "Trolley Member" }.take(120)
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                val updates = buildMap<String, Any> {
                    put("fullName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.trim().take(200))
                    put("dateOfBirth", trimmedDob)
                    put("ageVerified", true)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("fullName", safeName)
                    if (!email.isNullOrBlank()) put("email", email.trim().take(200))
                    put("dateOfBirth", trimmedDob)
                    put("ageVerified", true)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observePublishedProviders(): Flow<List<ProviderProfile>> = flow {
        val uid = requireUserId()
        val path = "providerProfiles"
        emitAll(
            db.collection("providerProfiles")
                .whereEqualTo("listingStatus", "published")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProviderProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProviderProfile(userId: String): Flow<ProviderProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "providerProfiles/$uid"
        emitAll(
            db.collection("providerProfiles").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(ProviderProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getProviderProfileOnce(userId: String): Result<ProviderProfile?> {
        val path = "providerProfiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val snapshot = db.collection("providerProfiles").document(uid).get().await()
            val profile = snapshot.toObject(ProviderProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createProviderDraft(
        userId: String,
        businessName: String,
        description: String,
        categorySlug: String,
        countryCode: String,
        city: String,
        businessAddress: String = "",
        latitude: Double? = null,
        longitude: Double? = null,
    ): Result<String> {
        val docPath = "providerProfiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val validated = validateProviderFields(
                businessName = businessName,
                description = description,
                categorySlug = categorySlug,
                countryCode = countryCode,
                city = city,
                businessAddress = businessAddress,
                latitude = latitude,
                longitude = longitude,
            )

            val payload = buildMap<String, Any> {
                put("userId", uid)
                put("businessName", validated.businessName)
                if (validated.description != null) put("description", validated.description)
                put("categorySlug", validated.categorySlug)
                put("categoryName", validated.categoryName)
                put("countryCode", validated.countryCode)
                put("city", validated.city)
                if (validated.businessAddress != null) put("businessAddress", validated.businessAddress)
                if (validated.latitude != null) put("latitude", validated.latitude)
                if (validated.longitude != null) put("longitude", validated.longitude)
                put("listingStatus", "draft")
                put("verificationStatus", "not_submitted")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            db.collection("providerProfiles").document(uid).set(payload).await()
            Result.success(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProviderDraft(
        userId: String,
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
    ): Result<Unit> {
        val docPath = "providerProfiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(currentStatus == "draft" || currentStatus == "pending_review") {
                "Only drafts and pending-review listings can be edited here."
            }
            val validated = validateProviderFields(
                businessName = businessName,
                description = description,
                categorySlug = categorySlug,
                countryCode = countryCode,
                city = city,
                businessAddress = businessAddress,
                latitude = latitude,
                longitude = longitude,
            )

            val updates = mutableMapOf<String, Any>(
                "businessName" to validated.businessName,
                "description" to (validated.description ?: FieldValue.delete()),
                "categorySlug" to validated.categorySlug,
                "categoryName" to validated.categoryName,
                "countryCode" to validated.countryCode,
                "city" to validated.city,
                "businessAddress" to (validated.businessAddress ?: FieldValue.delete()),
                "latitude" to (validated.latitude ?: FieldValue.delete()),
                "longitude" to (validated.longitude ?: FieldValue.delete()),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (submitForReview && currentStatus == "draft") {
                updates["listingStatus"] = "pending_review"
                updates["submittedAt"] = FieldValue.serverTimestamp()
            }

            db.collection("providerProfiles").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private data class ValidatedProviderInput(
        val businessName: String,
        val description: String?,
        val categorySlug: String,
        val categoryName: String,
        val countryCode: String,
        val city: String,
        val businessAddress: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    private fun validateProviderFields(
        businessName: String,
        description: String,
        categorySlug: String,
        countryCode: String,
        city: String,
        businessAddress: String,
        latitude: Double?,
        longitude: Double?,
    ): ValidatedProviderInput {
        val cleanName = businessName.trim()
        require(cleanName.length in 2..120) { "Business name must be 2 to 120 characters." }

        val cleanDescription = description.trim()
        require(cleanDescription.length <= 1200) { "Description must be at most 1200 characters." }

        val cleanCity = city.trim()
        require(cleanCity.length in 2..100) { "Enter a valid city (2 to 100 characters)." }

        val cleanAddress = businessAddress.trim().ifBlank { null }
        if (cleanAddress != null) {
            require(cleanAddress.length in 2..200) { "Business address must be 2 to 200 characters." }
        }

        if (latitude != null) {
            require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        }
        if (longitude != null) {
            require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        }

        val cleanCountry = countryCode.trim().uppercase(Locale.ENGLISH)
        require(cleanCountry.matches(Regex("^[A-Z]{2}$"))) { "Choose a valid two-letter country." }

        val category = ServiceTaxonomy.findBySlug(categorySlug.trim())
            ?: throw IllegalArgumentException("Choose an active service category.")

        return ValidatedProviderInput(
            businessName = cleanName,
            description = cleanDescription.ifBlank { null },
            categorySlug = category.slug,
            categoryName = category.name,
            countryCode = cleanCountry,
            city = cleanCity,
            businessAddress = cleanAddress,
            latitude = latitude,
            longitude = longitude,
        )
    }

    fun observeCustomerBookings(userId: String): Flow<List<BookingRequest>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "bookings"
        emitAll(
            db.collection("bookings")
                .whereEqualTo("customerId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookingRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedWith(
                        compareBy<BookingRequest> { it.scheduledDate }.thenBy { it.scheduledTime }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProviderBookings(userId: String): Flow<List<BookingRequest>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "bookings"
        emitAll(
            db.collection("bookings")
                .whereEqualTo("providerUserId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BookingRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedWith(
                        compareBy<BookingRequest> { it.scheduledDate }.thenBy { it.scheduledTime }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createBookingRequest(
        provider: ProviderProfile,
        customerName: String,
        scheduledDate: String,
        scheduledTime: String,
        notes: String,
    ): Result<String> {
        var docPath = "bookings"
        return try {
            val uid = requireUserId()
            val cleanCustomerName = customerName.trim().ifBlank {
                auth.currentUser?.displayName?.trim().orEmpty()
            }.ifBlank { "Trolley Member" }.take(120)

            require(provider.id.isNotBlank() && provider.userId.isNotBlank()) {
                "Selected provider profile reference is missing."
            }
            require(provider.listingStatus == "published" || provider.userId == uid) {
                "Bookings can only be requested for published providers or your own business preview."
            }

            val cleanDate = scheduledDate.trim()
            require(BookingValidator.isValidScheduledDate(cleanDate)) {
                "Choose today or a future date (YYYY-MM-DD)."
            }
            val cleanTime = scheduledTime.trim()
            require(BookingValidator.isValidScheduledTime(cleanTime)) {
                "Choose a valid time (HH:mm)."
            }
            val cleanNotes = notes.trim().ifBlank { null }
            if (cleanNotes != null) {
                require(cleanNotes.length <= 600) { "Notes must be 600 characters or fewer." }
            }

            val cleanProviderName = provider.businessName.trim().take(120)
            require(cleanProviderName.length >= 2) { "Invalid provider business name." }
            val cleanLocation = provider.formattedLocation.trim()
                .ifBlank { "${provider.city}, ${provider.countryCode}".trim() }
                .take(250)
            require(cleanLocation.length >= 2) { "Provider location is incomplete." }

            val docRef = db.collection("bookings").document()
            docPath = docRef.path
            val payload = buildMap<String, Any> {
                put("customerId", uid)
                put("customerName", cleanCustomerName)
                put("providerId", provider.id)
                put("providerUserId", provider.userId)
                put("providerBusinessName", cleanProviderName)
                put("categorySlug", provider.categorySlug)
                put("categoryName", provider.categoryName)
                put("providerLocation", cleanLocation)
                put("scheduledDate", cleanDate)
                put("scheduledTime", cleanTime)
                if (cleanNotes != null) put("notes", cleanNotes)
                put("status", "pending")
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun rescheduleBookingRequest(
        bookingId: String,
        scheduledDate: String,
        scheduledTime: String,
        notes: String,
    ): Result<Unit> {
        val docPath = "bookings/$bookingId"
        return try {
            requireUserId()
            require(bookingId.isNotBlank()) { "Booking ID is required." }
            val cleanDate = scheduledDate.trim()
            require(BookingValidator.isValidScheduledDate(cleanDate)) {
                "Choose today or a future date (YYYY-MM-DD)."
            }
            val cleanTime = scheduledTime.trim()
            require(BookingValidator.isValidScheduledTime(cleanTime)) {
                "Choose a valid time (HH:mm)."
            }
            val cleanNotes = notes.trim().ifBlank { null }
            if (cleanNotes != null) {
                require(cleanNotes.length <= 600) { "Notes must be 600 characters or fewer." }
            }

            db.collection("bookings").document(bookingId).update(
                mapOf(
                    "scheduledDate" to cleanDate,
                    "scheduledTime" to cleanTime,
                    "notes" to (cleanNotes ?: FieldValue.delete()),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatus(
        booking: BookingRequest,
        nextStatus: String,
        asProvider: Boolean,
    ): Result<Unit> {
        val docPath = "bookings/${booking.id}"
        return try {
            val uid = requireUserId()
            require(booking.id.isNotBlank()) { "Booking ID is required." }
            if (asProvider) {
                check(booking.providerUserId == uid) { "Only the linked provider can update this booking." }
            } else {
                check(booking.customerId == uid) { "Only the requester can cancel this booking." }
            }
            require(BookingValidator.isAllowedStatusTransition(booking.status, nextStatus, asProvider)) {
                "Cannot change booking from ${booking.status} to $nextStatus."
            }

            db.collection("bookings").document(booking.id).update(
                mapOf(
                    "status" to nextStatus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeProviderProfileById(providerId: String): Flow<ProviderProfile?> = flow {
        val uid = requireUserId()
        require(providerId.isNotBlank()) { "Provider ID is required." }
        val path = "providerProfiles/$providerId"
        emitAll(
            db.collection("providerProfiles").document(providerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ProviderProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeProviderReviews(providerId: String): Flow<List<ProviderReview>> = flow {
        val uid = requireUserId()
        require(providerId.isNotBlank()) { "Provider ID is required." }
        val path = "reviews"
        emitAll(
            db.collection("reviews")
                .whereEqualTo("providerId", providerId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ProviderReview::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllProviderRatings(): Flow<Map<String, ProviderRatingSummary>> = flow {
        val uid = requireUserId()
        val path = "reviews"
        emitAll(
            db.collection("reviews")
                .snapshots()
                .map { snapshot ->
                    val reviews = snapshot.toObjects(
                        ProviderReview::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                    ReviewCalculator.computeAllSummaries(reviews)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitProviderReview(
        provider: ProviderProfile,
        reviewerName: String,
        rating: Int,
        comment: String,
        bookingId: String? = null,
    ): Result<String> {
        var docPath = "reviews"
        return try {
            val uid = requireUserId()
            require(provider.id.isNotBlank() && provider.userId.isNotBlank()) {
                "Selected provider profile reference is missing."
            }
            require(provider.userId != uid) {
                "You cannot submit a customer rating for your own provider profile."
            }
            require(provider.listingStatus == "published") {
                "Reviews can only be submitted for published providers."
            }
            require(ReviewCalculator.isValidRating(rating)) {
                "Please select a rating from 1 to 5 stars."
            }
            val cleanComment = comment.trim()
            require(ReviewCalculator.isValidComment(cleanComment)) {
                "Feedback must be between 2 and 1000 characters."
            }
            val cleanName = reviewerName.trim().ifBlank {
                auth.currentUser?.displayName?.trim().orEmpty()
            }.ifBlank { "Trolley Member" }.take(120)
            val cleanBookingId = bookingId?.trim()?.takeIf { it.isNotBlank() }

            val reviewId = ReviewCalculator.deterministicReviewId(provider.id, uid)
            val docRef = db.collection("reviews").document(reviewId)
            docPath = docRef.path
            val existing = docRef.get().await()

            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "reviewerName" to cleanName,
                    "rating" to rating,
                    "comment" to cleanComment,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (cleanBookingId != null) {
                    updates["bookingId"] = cleanBookingId
                } else if (existing.contains("bookingId")) {
                    updates["bookingId"] = FieldValue.delete()
                }
                docRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("providerId", provider.id)
                    put("providerUserId", provider.userId)
                    put("customerId", uid)
                    put("reviewerName", cleanName)
                    put("rating", rating)
                    put("comment", cleanComment)
                    if (cleanBookingId != null) put("bookingId", cleanBookingId)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            }
            Result.success(reviewId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProviderReview(providerId: String): Result<Unit> {
        var docPath = "reviews"
        return try {
            val uid = requireUserId()
            require(providerId.isNotBlank()) { "Provider ID is required." }
            val reviewId = ReviewCalculator.deterministicReviewId(providerId, uid)
            docPath = "reviews/$reviewId"
            db.collection("reviews").document(reviewId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeProviderSubscription(userId: String): Flow<ProviderSubscription?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "providerSubscriptions/$uid"
        emitAll(
            db.collection("providerSubscriptions").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ProviderSubscription::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeBillingPayments(userId: String): Flow<List<BillingPayment>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "billingPayments"
        emitAll(
            db.collection("billingPayments")
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BillingPayment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeProviderSecurityConfig(userId: String): Flow<ProviderSecurityConfig?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "providerSecurity/$uid"
        emitAll(
            db.collection("providerSecurity").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        ProviderSecurityConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProviderSecurityEnrollment(
        userId: String,
        secretFingerprint: String,
        hasNativeFirebaseMfa: Boolean,
    ): Result<Unit> {
        val docPath = "providerSecurity/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user 2FA write." }
            val cleanFp = secretFingerprint.trim().lowercase(Locale.ENGLISH)
            require(cleanFp.matches(Regex("^[a-f0-9]{64}$"))) {
                "Invalid 2FA key fingerprint format."
            }
            val factorType = if (hasNativeFirebaseMfa) "firebase_mfa_and_totp" else "totp_authenticator"
            val docRef = db.collection("providerSecurity").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "twoFactorEnabled" to true,
                        "twoFactorType" to factorType,
                        "secretFingerprint" to cleanFp,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "providerId" to uid,
                        "twoFactorEnabled" to true,
                        "twoFactorType" to factorType,
                        "secretFingerprint" to cleanFp,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordFinancialVerificationAudit(
        userId: String,
        firebaseAuthTimeSeconds: Long,
        hasNativeFirebaseMfa: Boolean,
        actionType: String = "activate_subscription",
    ): Result<String> {
        var docPath = "financialVerifications"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user verification log." }
            require(firebaseAuthTimeSeconds > 0L) {
                "Firebase Auth step-up timestamp is missing. Re-authenticate with Google first."
            }
            require(actionType in setOf("activate_subscription", "switch_plan", "update_payment_method")) {
                "Unsupported financial verification action."
            }
            val method = if (hasNativeFirebaseMfa) {
                "firebase_native_mfa_and_totp"
            } else {
                "firebase_reauth_and_totp"
            }
            val docRef = db.collection("financialVerifications").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "providerId" to uid,
                    "verificationMethod" to method,
                    "reauthenticated" to true,
                    "totpVerified" to true,
                    "firebaseAuthTimeSeconds" to firebaseAuthTimeSeconds,
                    "actionType" to actionType,
                    "verifiedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun activateProviderSubscription(
        userId: String,
        businessName: String,
        tier: SubscriptionTier,
        cycle: BillingCycle,
        method: PaymentMethodType,
        autoRenew: Boolean,
        mobileNumber: String = "",
        cardholderName: String = "",
        cardNumber: String = "",
        cardExpiry: String = "",
        cardCvc: String = "",
        paypalEmail: String = "",
        bankAccountRef: String = "",
        verificationId: String? = null,
    ): Result<String> {
        var docPath = "providerSubscriptions/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user billing write." }
            val cleanBusiness = businessName.trim().take(120)
            require(cleanBusiness.length >= 2) {
                "Create your provider business profile before choosing a billing plan."
            }

            val quote = BillingCatalog.computeQuote(tier, cycle)
            val cleanVerificationId = verificationId?.trim()?.takeIf { it.isNotBlank() }
            if (quote.amountCents > 0) {
                require(cleanVerificationId != null) {
                    "Two-factor authentication (Firebase Auth + 6-digit TOTP) is required before completing paid transactions."
                }
            }

            val effectiveMethod = if (tier == SubscriptionTier.FREE_STARTER) PaymentMethodType.NONE else method
            val validation = PaymentValidator.validateAndMask(
                tier = tier,
                method = effectiveMethod,
                mobileNumber = mobileNumber,
                cardholderName = cardholderName,
                cardNumber = cardNumber,
                cardExpiry = cardExpiry,
                cardCvc = cardCvc,
                paypalEmail = paypalEmail,
                bankAccountLast4OrRef = bankAccountRef,
            )
            require(validation.isValid) {
                validation.errorMessage ?: "Payment details failed security validation."
            }
            require(PaymentValidator.isSafeMaskedInstrument(validation.maskedInstrument)) {
                "Payment instrument masking safety check failed."
            }

            val invoiceNumber = BillingCatalog.generateInvoiceNumber(uid)
            val effectiveAutoRenew = tier != SubscriptionTier.FREE_STARTER &&
                effectiveMethod.supportsAutoRenew &&
                autoRenew

            val subRef = db.collection("providerSubscriptions").document(uid)
            val existingSub = subRef.get().await()

            if (existingSub.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "businessName" to cleanBusiness,
                    "planTier" to tier.id,
                    "billingCycle" to cycle.id,
                    "paymentMethod" to effectiveMethod.id,
                    "currency" to BillingCatalog.CURRENCY_USD,
                    "amountCents" to quote.amountCents,
                    "monthlyBookingLimit" to tier.monthlyBookingLimit,
                    "maxCategories" to tier.maxCategories,
                    "subscriptionStatus" to validation.subscriptionStatus,
                    "autoRenew" to effectiveAutoRenew,
                    "maskedInstrument" to validation.maskedInstrument,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (cleanVerificationId != null) {
                    updates["verificationId"] = cleanVerificationId
                } else if (existingSub.contains("verificationId")) {
                    updates["verificationId"] = FieldValue.delete()
                }
                subRef.update(updates).await()
            } else {
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("providerId", uid)
                    put("businessName", cleanBusiness)
                    put("planTier", tier.id)
                    put("billingCycle", cycle.id)
                    put("paymentMethod", effectiveMethod.id)
                    put("currency", BillingCatalog.CURRENCY_USD)
                    put("amountCents", quote.amountCents)
                    put("monthlyBookingLimit", tier.monthlyBookingLimit)
                    put("maxCategories", tier.maxCategories)
                    put("subscriptionStatus", validation.subscriptionStatus)
                    put("autoRenew", effectiveAutoRenew)
                    put("maskedInstrument", validation.maskedInstrument)
                    if (cleanVerificationId != null) put("verificationId", cleanVerificationId)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                subRef.set(payload).await()
            }

            val paymentRef = db.collection("billingPayments").document()
            docPath = paymentRef.path
            val paymentPayload = buildMap<String, Any> {
                put("userId", uid)
                put("providerId", uid)
                put("planTier", tier.id)
                put("billingCycle", cycle.id)
                put("paymentMethod", effectiveMethod.id)
                put("currency", BillingCatalog.CURRENCY_USD)
                put("subtotalCents", quote.subtotalCents)
                put("discountCents", quote.discountCents)
                put("amountCents", quote.amountCents)
                put("maskedInstrument", validation.maskedInstrument)
                put("invoiceNumber", invoiceNumber)
                put("paymentStatus", validation.paymentStatus)
                if (cleanVerificationId != null) put("verificationId", cleanVerificationId)
                put("createdAt", FieldValue.serverTimestamp())
            }
            paymentRef.set(paymentPayload).await()

            Result.success(invoiceNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeChatThreads(userId: String): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        val path = "chatThreads"
        emitAll(
            db.collection("chatThreads")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedByDescending {
                        it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChatThread(threadId: String, userId: String): Flow<ChatThread?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        require(threadId.isNotBlank()) { "Thread ID is required." }
        val path = "chatThreads/$threadId"
        emitAll(
            db.collection("chatThreads")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).firstOrNull { it.id == threadId }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeChatMessages(threadId: String, userId: String): Flow<List<ChatMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch." }
        require(threadId.isNotBlank()) { "Thread ID is required." }
        val path = "chatThreads/$threadId/messages"
        emitAll(
            db.collection("chatThreads")
                .document(threadId)
                .collection("messages")
                .whereArrayContains("participantIds", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ).sortedWith(
                        compareBy<ChatMessage>(
                            { it.sentAt?.toDate()?.time ?: it.clientSentMillis },
                            { it.clientSentMillis },
                            { it.id },
                        )
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun sendChatMessage(
        providerId: String,
        providerUserId: String,
        providerBusinessName: String,
        providerCategoryName: String,
        customerId: String,
        customerName: String,
        senderName: String,
        body: String,
        messageType: String = "text",
    ): Result<String> {
        val cleanProviderId = providerId.trim()
        val cleanProviderUid = providerUserId.trim()
        val cleanCustomerId = customerId.trim()
        val threadId = ChatValidator.deterministicThreadId(cleanProviderId, cleanCustomerId)
        val docPath = "chatThreads/$threadId/messages"
        return try {
            val uid = requireUserId()
            require(cleanProviderId.isNotBlank() && cleanProviderUid.isNotBlank() && cleanCustomerId.isNotBlank()) {
                "Conversation participant identifiers are missing."
            }
            require(cleanCustomerId != cleanProviderUid) {
                "You cannot start a pre-booking chat with your own provider profile."
            }
            require(uid == cleanCustomerId || uid == cleanProviderUid) {
                "You are not a participant in this conversation."
            }
            val cleanBody = body.trim()
            require(ChatValidator.isValidMessageBody(cleanBody)) {
                "Message must be between 1 and 1,000 characters."
            }
            val cleanCustomerName = customerName.trim().ifBlank { "Trolley Customer" }.take(120)
            val cleanSenderName = senderName.trim().ifBlank {
                if (uid == cleanProviderUid) providerBusinessName else cleanCustomerName
            }.take(120)
            val cleanBizName = providerBusinessName.trim().ifBlank { "Service Provider" }.take(120)
            val cleanCategoryName = providerCategoryName.trim().ifBlank { "Services" }.take(120)
            val safeMessageType = ChatValidator.resolveMessageType(messageType)
            val participants = ChatValidator.buildParticipantIds(cleanCustomerId, cleanProviderUid)

            val threadRef = db.collection("chatThreads").document(threadId)
            val existingThread = threadRef.get().await()
            val messageRef = threadRef.collection("messages").document()
            val nowMs = System.currentTimeMillis().coerceAtLeast(1L)

            val batch = db.batch()
            if (!existingThread.exists()) {
                require(uid == cleanCustomerId) {
                    "Only customers can initiate a new pre-booking inquiry thread."
                }
                val threadPayload = buildMap<String, Any> {
                    put("customerId", cleanCustomerId)
                    put("providerId", cleanProviderId)
                    put("providerUserId", cleanProviderUid)
                    put("participantIds", participants)
                    put("customerName", cleanCustomerName)
                    put("providerBusinessName", cleanBizName)
                    put("providerCategoryName", cleanCategoryName)
                    put("lastMessageText", cleanBody)
                    put("lastSenderId", uid)
                    put("messageCount", 1)
                    put("lastCustomerReadAt", FieldValue.serverTimestamp())
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                batch.set(threadRef, threadPayload)
            } else {
                val updates = mutableMapOf<String, Any>(
                    "lastMessageText" to cleanBody,
                    "lastSenderId" to uid,
                    "messageCount" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (uid == cleanCustomerId) {
                    updates["customerName"] = cleanCustomerName
                    updates["lastCustomerReadAt"] = FieldValue.serverTimestamp()
                } else {
                    updates["lastProviderReadAt"] = FieldValue.serverTimestamp()
                }
                batch.update(threadRef, updates)
            }

            val messagePayload = buildMap<String, Any> {
                put("threadId", threadId)
                put("providerId", cleanProviderId)
                put("providerUserId", cleanProviderUid)
                put("customerId", cleanCustomerId)
                put("participantIds", participants)
                put("senderId", uid)
                put("senderName", cleanSenderName)
                put("body", cleanBody)
                put("messageType", safeMessageType)
                put("clientSentMillis", nowMs)
                put("sentAt", FieldValue.serverTimestamp())
            }
            batch.set(messageRef, messagePayload)
            batch.commit().await()

            Result.success(messageRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markChatThreadRead(threadId: String): Result<Unit> {
        val cleanThreadId = threadId.trim()
        val docPath = "chatThreads/$cleanThreadId"
        return try {
            val uid = requireUserId()
            if (cleanThreadId.isBlank()) return Result.success(Unit)
            val threadRef = db.collection("chatThreads").document(cleanThreadId)
            val snap = threadRef.get().await()
            if (!snap.exists()) return Result.success(Unit)
            val thread = snap.toObject(
                ChatThread::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: return Result.success(Unit)
            if (uid !in thread.participantIds || !thread.hasUnreadFor(uid)) {
                return Result.success(Unit)
            }
            val readField = if (uid == thread.customerId) "lastCustomerReadAt" else "lastProviderReadAt"
            val updates = mapOf<String, Any>(
                readField to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            threadRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
