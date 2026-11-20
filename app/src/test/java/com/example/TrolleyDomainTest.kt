package com.example

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.catalog.GeographyCatalog
import com.example.data.catalog.ServiceTaxonomy
import com.example.data.firebase.AuthManager
import com.example.data.firebase.GoogleSignInResult
import com.example.data.location.AddressGeocoder
import com.example.data.model.BookingValidator
import com.example.data.model.ProviderReview
import com.example.data.model.ReviewCalculator
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrolleyDomainTest {

    @Test
    fun taxonomy_contains51FamiliesAnd1020UniqueSubcategories() {
        assertEquals(51, ServiceTaxonomy.familyNames.size)
        assertEquals(1020, ServiceTaxonomy.subcategoryCount)
        assertEquals(1020, ServiceTaxonomy.categories.map { it.slug }.toSet().size)
        assertNotNull(ServiceTaxonomy.findBySlug("plumbing-leak-detection"))
    }

    @Test
    fun geography_includesAllRegionsAndZimbabweDefault() {
        assertEquals(7, GeographyCatalog.regions.size)
        assertEquals("Zimbabwe", GeographyCatalog.getCountryLabel("ZW"))
        assertEquals("Africa", GeographyCatalog.getRegionForCountry("ZW"))
        assertTrue(GeographyCatalog.countries.all { it.code.length == 2 })

        val (harareLat, harareLon) = GeographyCatalog.approximateCoordinates("ZW", "Harare")
        assertTrue(harareLat in -18.5..-17.0)
        assertTrue(harareLon in 30.0..32.0)

        val distanceKm = AddressGeocoder.haversineDistanceKm(
            lat1 = -17.8252,
            lon1 = 31.0335,
            lat2 = -20.1325,
            lon2 = 28.6265,
        )
        assertTrue(distanceKm in 350.0..375.0)
    }

    @Test
    fun search_filtersByQueryAndCategoryFamily() {
        val plumbingMatches = ServiceTaxonomy.filterServices(query = "", activeFamily = "Plumbing")
        assertTrue(plumbingMatches.any { it.slug == "plumbing-leak-detection" })
        assertFalse(plumbingMatches.any { it.family != "Plumbing" })

        val combinedMatches = ServiceTaxonomy.filterServices(query = "leak", activeFamily = "Plumbing")
        assertTrue(combinedMatches.any { it.slug == "plumbing-leak-detection" })
        assertFalse(combinedMatches.any { it.family != "Plumbing" })

        val mismatchedCategory = ServiceTaxonomy.filterServices(query = "leak", activeFamily = "Accounting")
        assertFalse(mismatchedCategory.any { it.slug == "plumbing-leak-detection" })
    }

    @Test
    fun bookingValidator_validatesDatesTimesAndTransitions() {
        val referenceToday = LocalDate.of(2026, 11, 20)
        assertTrue(BookingValidator.isValidScheduledDate("2026-11-20", today = referenceToday))
        assertTrue(BookingValidator.isValidScheduledDate("2026-12-05", today = referenceToday))
        assertFalse(BookingValidator.isValidScheduledDate("2026-11-19", today = referenceToday))
        assertFalse(BookingValidator.isValidScheduledDate("2026-02-30", today = referenceToday))
        assertFalse(BookingValidator.isValidScheduledDate("20/11/2026", today = referenceToday))

        assertTrue(BookingValidator.isValidScheduledTime("09:00"))
        assertTrue(BookingValidator.isValidScheduledTime("23:59"))
        assertFalse(BookingValidator.isValidScheduledTime("24:00"))
        assertFalse(BookingValidator.isValidScheduledTime("9:00"))

        assertTrue(BookingValidator.isAllowedStatusTransition("pending", "accepted", asProvider = true))
        assertTrue(BookingValidator.isAllowedStatusTransition("pending", "declined", asProvider = true))
        assertTrue(BookingValidator.isAllowedStatusTransition("accepted", "completed", asProvider = true))
        assertFalse(BookingValidator.isAllowedStatusTransition("pending", "completed", asProvider = true))

        assertTrue(BookingValidator.isAllowedStatusTransition("pending", "cancelled", asProvider = false))
        assertTrue(BookingValidator.isAllowedStatusTransition("accepted", "cancelled", asProvider = false))
        assertFalse(BookingValidator.isAllowedStatusTransition("pending", "accepted", asProvider = false))
        assertFalse(BookingValidator.isAllowedStatusTransition("completed", "cancelled", asProvider = false))
    }

    @Test
    fun reviewCalculator_computesAverageScoreCountsAndValidation() {
        assertEquals("prov1__cust1", ReviewCalculator.deterministicReviewId("prov1", "cust1"))
        assertTrue(ReviewCalculator.isValidRating(1))
        assertTrue(ReviewCalculator.isValidRating(5))
        assertFalse(ReviewCalculator.isValidRating(0))
        assertFalse(ReviewCalculator.isValidRating(6))

        assertTrue(ReviewCalculator.isValidComment("Great plumbing repair"))
        assertFalse(ReviewCalculator.isValidComment(" "))
        assertFalse(ReviewCalculator.isValidComment("A"))

        val reviews = listOf(
            ProviderReview(id = "p1__u1", providerId = "p1", customerId = "u1", rating = 5, comment = "Excellent!"),
            ProviderReview(id = "p1__u2", providerId = "p1", customerId = "u2", rating = 4, comment = "Solid job"),
            ProviderReview(id = "p1__u3", providerId = "p1", customerId = "u3", rating = 5, comment = "Fast"),
        )
        val summary = ReviewCalculator.computeSummary("p1", reviews)
        assertEquals(3, summary.reviewCount)
        assertEquals(4.666, summary.averageRating, 0.01)
        assertEquals("4.7", summary.formattedAverage)
        assertEquals(2, summary.ratingCounts[5])
        assertEquals(1, summary.ratingCounts[4])
        assertEquals(0, summary.ratingCounts[1])

        val emptySummary = ReviewCalculator.computeSummary("p_empty", emptyList())
        assertEquals(0, emptySummary.reviewCount)
        assertEquals("0.0", emptySummary.formattedAverage)
    }

    @Test
    fun googleAuthManager_validatesClientIdTimeoutsAndExceptions() {
        // Web Client ID validation matching google-services.json
        assertTrue(
            AuthManager.isValidWebClientId(
                "321939409654-mj0qg1l2e6msvr8obvhtn8hagq97q3up.apps.googleusercontent.com"
            )
        )
        assertFalse(AuthManager.isValidWebClientId(""))
        assertFalse(AuthManager.isValidWebClientId(null))
        assertFalse(AuthManager.isValidWebClientId("not-a-google-client-id"))

        // 45s interactive timeout & 10m verification window math
        assertEquals(45_000L, AuthManager.LOGIN_TIMEOUT_MS)
        assertEquals(600_000L, AuthManager.VERIFICATION_WINDOW_MS)
        val signInTime = 1_000_000L
        assertEquals(360_000L, AuthManager.computeRemainingVerificationMillis(signInTime, signInTime + 240_000L))
        assertEquals(0L, AuthManager.computeRemainingVerificationMillis(signInTime, signInTime + 650_000L))

        // Credential Manager exception mapping for reliable error handling
        assertEquals(
            GoogleSignInResult.Cancelled,
            AuthManager.mapGoogleCredentialException(GetCredentialCancellationException("User cancelled")),
        )

        val noCred = AuthManager.mapGoogleCredentialException(NoCredentialException("No credentials"))
        assertTrue(noCred is GoogleSignInResult.Error && noCred.message.contains("Google account", ignoreCase = true))

        val interrupted = AuthManager.mapGoogleCredentialException(GetCredentialInterruptedException("Interrupted"))
        assertTrue(interrupted is GoogleSignInResult.Error && interrupted.message.contains("interrupted", ignoreCase = true))

        val unsupported = AuthManager.mapGoogleCredentialException(GetCredentialProviderConfigurationException("Missing provider"))
        assertTrue(unsupported is GoogleSignInResult.Error && unsupported.message.contains("Play Services", ignoreCase = true))

        val parseError = AuthManager.mapGoogleCredentialException(GoogleIdTokenParsingException(RuntimeException("bad jwt")))
        assertTrue(parseError is GoogleSignInResult.Error && parseError.message.contains("ID token", ignoreCase = true))

        // Google Sign-Up vs Sign-In success model representation
        val signUpResult = GoogleSignInResult.Success(
            isNewUser = true,
            userId = "uid_1",
            displayName = "Tinashe",
            email = "tinashe@example.com",
            photoUrl = null,
        )
        assertTrue(signUpResult.isNewUser)
    }
}
