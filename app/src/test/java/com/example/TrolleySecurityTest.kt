package com.example

import com.example.data.catalog.BillingCatalog
import com.example.data.catalog.BillingCycle
import com.example.data.catalog.PaymentMethodType
import com.example.data.catalog.PaymentValidator
import com.example.data.catalog.SubscriptionTier
import com.example.data.catalog.formatUsdCents
import com.example.data.firebase.AuthManager
import com.example.data.model.BookingValidator
import com.example.data.model.ChatThread
import com.example.data.model.ChatValidator
import com.example.data.model.ReviewCalculator
import com.example.data.security.LocalTwoFactorStatus
import com.example.data.security.TotpVerifyOutcome
import com.example.data.security.TwoFactorAuthenticator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TrolleySecurityTest {

    private val referenceMonth = YearMonth.of(2026, 11)

    @Test
    fun freeStarterTier_hasExactlyFourFreeFeaturesWithLimitsAndZeroPrice() {
        val free = SubscriptionTier.FREE_STARTER
        assertEquals(0, free.monthlyCents)
        assertEquals(3, free.monthlyBookingLimit)
        assertEquals(1, free.maxCategories)
        assertEquals(4, free.features.size)

        val monthlyQuote = BillingCatalog.computeQuote(free, BillingCycle.MONTHLY)
        val yearlyQuote = BillingCatalog.computeQuote(free, BillingCycle.YEARLY)
        assertEquals(0, monthlyQuote.amountCents)
        assertEquals(0, yearlyQuote.amountCents)
        assertEquals("$0.00", monthlyQuote.formattedPrice)
    }

    @Test
    fun paidTiers_businessEliteCapsAt999MonthlyAndComputesExact30PercentYearlyDiscount() {
        val highestMonthlyCents = SubscriptionTier.entries.maxOf { it.monthlyCents }
        assertEquals(999, highestMonthlyCents)

        val eliteMonthly = BillingCatalog.computeQuote(SubscriptionTier.BUSINESS_ELITE, BillingCycle.MONTHLY)
        assertEquals(999, eliteMonthly.amountCents)
        assertEquals("$9.99", eliteMonthly.formattedPrice)

        // $9.99 * 12 = $119.88 (11988 cents); 30% discount = 3596 cents -> $83.92 (8392 cents)
        val eliteYearly = BillingCatalog.computeQuote(SubscriptionTier.BUSINESS_ELITE, BillingCycle.YEARLY)
        assertEquals(11988, eliteYearly.subtotalCents)
        assertEquals(3596, eliteYearly.discountCents)
        assertEquals(8392, eliteYearly.amountCents)
        assertEquals("$83.92", eliteYearly.formattedPrice)
        assertEquals("$119.88", eliteYearly.formattedSubtotal)
        assertEquals(30, eliteYearly.savingsPercent)

        // $4.99 * 12 = $59.88 (5988 cents); 30% discount = 1796 cents -> $41.92 (4192 cents)
        val proYearly = BillingCatalog.computeQuote(SubscriptionTier.GROWTH_PRO, BillingCycle.YEARLY)
        assertEquals(5988, proYearly.subtotalCents)
        assertEquals(1796, proYearly.discountCents)
        assertEquals(4192, proYearly.amountCents)
        assertEquals("$41.92", proYearly.formattedPrice)
        assertEquals("$4.99", formatUsdCents(SubscriptionTier.GROWTH_PRO.monthlyCents))
    }

    @Test
    fun visaCheckout_enforcesLuhnBinExpiryCvcAndMasksRawPan() {
        val validVisaPan = "4111111111111111"
        val result = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.BUSINESS_ELITE,
            method = PaymentMethodType.VISA,
            cardholderName = "Tadiwa Moyo",
            cardNumber = "4111 1111 1111 1111",
            cardExpiry = "12/28",
            cardCvc = "123",
            currentMonth = referenceMonth,
        )
        assertTrue(result.isValid)
        assertEquals("Visa •••• 1111", result.maskedInstrument)
        assertFalse(result.maskedInstrument.contains("411111"))
        assertFalse(result.maskedInstrument.contains("123"))
        assertTrue(PaymentValidator.isSafeMaskedInstrument(result.maskedInstrument))
        assertTrue(PaymentValidator.passesLuhnCheck(validVisaPan))

        // Corrupt last digit -> Luhn check must reject
        val badLuhn = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.BUSINESS_ELITE,
            method = PaymentMethodType.VISA,
            cardholderName = "Tadiwa Moyo",
            cardNumber = "4111 1111 1111 1112",
            cardExpiry = "12/28",
            cardCvc = "123",
            currentMonth = referenceMonth,
        )
        assertFalse(badLuhn.isValid)

        // Mastercard BIN submitted under Visa must fail brand BIN verification
        val crossBrand = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.BUSINESS_ELITE,
            method = PaymentMethodType.VISA,
            cardholderName = "Tadiwa Moyo",
            cardNumber = "5555 5555 5555 4444",
            cardExpiry = "12/28",
            cardCvc = "123",
            currentMonth = referenceMonth,
        )
        assertFalse(crossBrand.isValid)
    }

    @Test
    fun mastercardCheckout_enforcesBoth5SeriesAnd2SeriesBinsAndRejectsExpiredCard() {
        val mc5Series = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.MASTERCARD,
            cardholderName = "Rudo Chikwanda",
            cardNumber = "5555 5555 5555 4444",
            cardExpiry = "11/26",
            cardCvc = "456",
            currentMonth = referenceMonth,
        )
        assertTrue(mc5Series.isValid)
        assertEquals("Mastercard •••• 4444", mc5Series.maskedInstrument)

        val mc2Series = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.MASTERCARD,
            cardholderName = "Rudo Chikwanda",
            cardNumber = "2223 0000 4840 0011",
            cardExpiry = "05/29",
            cardCvc = "998",
            currentMonth = referenceMonth,
        )
        assertTrue(mc2Series.isValid)
        assertEquals("Mastercard •••• 0011", mc2Series.maskedInstrument)

        // Expired card must be rejected
        val expired = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.MASTERCARD,
            cardholderName = "Rudo Chikwanda",
            cardNumber = "5555 5555 5555 4444",
            cardExpiry = "10/26",
            cardCvc = "456",
            currentMonth = referenceMonth,
        )
        assertFalse(expired.isValid)

        // Short 2-digit CVC rejected
        val badCvc = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.MASTERCARD,
            cardholderName = "Rudo Chikwanda",
            cardNumber = "5555 5555 5555 4444",
            cardExpiry = "12/28",
            cardCvc = "45",
            currentMonth = referenceMonth,
        )
        assertFalse(badCvc.isValid)
    }

    @Test
    fun ecocashAndOtherBillingSystems_enforceStrictInputAndPiiMasking() {
        val ecoValid = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.BUSINESS_ELITE,
            method = PaymentMethodType.ECOCASH,
            mobileNumber = "0771234567",
        )
        assertTrue(ecoValid.isValid)
        assertEquals("EcoCash +263 77***567", ecoValid.maskedInstrument)
        assertFalse(ecoValid.maskedInstrument.contains("1234"))
        assertTrue(PaymentValidator.isSafeMaskedInstrument(ecoValid.maskedInstrument))

        // Invalid Econet prefix (NetOne 071 rejected for EcoCash)
        val wrongPrefix = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.BUSINESS_ELITE,
            method = PaymentMethodType.ECOCASH,
            mobileNumber = "0711234567",
        )
        assertFalse(wrongPrefix.isValid)

        // InnBucks wallet validation & masking
        val innbucksValid = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.INNBUCKS,
            mobileNumber = "+263712345678",
        )
        assertTrue(innbucksValid.isValid)
        assertEquals("InnBucks +263 ***678", innbucksValid.maskedInstrument)

        // PayPal validation & email masking
        val paypalValid = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.PAYPAL,
            paypalEmail = "tadiwa.pro@example.com",
        )
        assertTrue(paypalValid.isValid)
        assertEquals("PayPal t***@example.com", paypalValid.maskedInstrument)
        assertFalse(PaymentValidator.validateAndMask(SubscriptionTier.GROWTH_PRO, PaymentMethodType.PAYPAL, paypalEmail = "bad-email").isValid)

        // Bank EFT must reject full bank account numbers (>4 digits)
        val eftLeakedAccount = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.BANK_EFT,
            bankAccountLast4OrRef = "CBZ 1234567890",
        )
        assertFalse(eftLeakedAccount.isValid)
        val eftValid = PaymentValidator.validateAndMask(
            tier = SubscriptionTier.GROWTH_PRO,
            method = PaymentMethodType.BANK_EFT,
            bankAccountLast4OrRef = "CBZ 4521",
        )
        assertTrue(eftValid.isValid)
        assertEquals("Bank EFT •••• 4521", eftValid.maskedInstrument)

        // Paid plan cannot use NONE method
        assertFalse(PaymentValidator.validateAndMask(SubscriptionTier.GROWTH_PRO, PaymentMethodType.NONE).isValid)
    }

    @Test
    fun securityMaskChecker_rejectsAnyLongDigitRunToPreventPanOrAccountLeakage() {
        assertFalse(PaymentValidator.isSafeMaskedInstrument("Card 411111111111"))
        assertFalse(PaymentValidator.isSafeMaskedInstrument("EcoCash 0771234567"))
        assertFalse(PaymentValidator.isSafeMaskedInstrument("AB"))
        assertTrue(PaymentValidator.isSafeMaskedInstrument("Visa •••• 4242"))
    }

    @Test
    fun bookingAndReviewValidators_enforcePrivilegeBoundaries() {
        val today = LocalDate.of(2026, 11, 20)
        assertFalse(BookingValidator.isValidScheduledDate("2026-11-19", today))
        assertTrue(BookingValidator.isValidScheduledDate("2026-11-20", today))
        assertFalse(BookingValidator.isValidScheduledTime("24:00"))
        assertTrue(BookingValidator.isValidScheduledTime("09:30"))

        // Customer cannot approve or complete a booking; provider cannot hijack completed state
        assertFalse(BookingValidator.isAllowedStatusTransition("pending", "accepted", asProvider = false))
        assertFalse(BookingValidator.isAllowedStatusTransition("pending", "completed", asProvider = false))
        assertFalse(BookingValidator.isAllowedStatusTransition("completed", "pending", asProvider = true))
        assertTrue(BookingValidator.isAllowedStatusTransition("accepted", "completed", asProvider = true))

        // Rating bounds
        assertFalse(ReviewCalculator.isValidRating(0))
        assertFalse(ReviewCalculator.isValidRating(6))
        assertTrue(ReviewCalculator.isValidRating(5))
        assertFalse(ReviewCalculator.isValidComment("A"))
        assertTrue(ReviewCalculator.isValidComment("OK"))
    }

    @Test
    fun twoFactorTotp_matchesRfc6238Sha1VectorsAndBase32RoundTrip() {
        val rawBytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val secretBase32 = TwoFactorAuthenticator.encodeBase32(rawBytes)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", secretBase32)
        assertTrue(TwoFactorAuthenticator.decodeBase32(secretBase32).contentEquals(rawBytes))

        // RFC 6238 SHA-1 vectors truncated to 6 digits (step = 30s)
        assertEquals("287082", TwoFactorAuthenticator.computeTotpCode(secretBase32, timeStep = 59L / 30L))
        assertEquals("081804", TwoFactorAuthenticator.computeTotpCode(secretBase32, timeStep = 1111111109L / 30L))
        assertEquals("050471", TwoFactorAuthenticator.computeTotpCode(secretBase32, timeStep = 1111111111L / 30L))
        assertEquals("005924", TwoFactorAuthenticator.computeTotpCode(secretBase32, timeStep = 1234567890L / 30L))
    }

    @Test
    fun twoFactorTotp_enforcesWindowDriftReplayProtectionAndFormat() {
        val secret = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"
        val nowMillis = 1_760_000_000_000L // arbitrary clean epoch ms
        val baseStep = TwoFactorAuthenticator.currentTimeStep(nowMillis)

        val currentCode = TwoFactorAuthenticator.computeTotpCode(secret, baseStep)
        val prevCode = TwoFactorAuthenticator.computeTotpCode(secret, baseStep - 1)
        val expiredCode = TwoFactorAuthenticator.computeTotpCode(secret, baseStep - 3)

        // Valid current and -1 step drift codes succeed
        val currentResult = TwoFactorAuthenticator.verifyTotpCode(secret, currentCode, nowMillis = nowMillis)
        assertEquals(TotpVerifyOutcome.Verified(baseStep), currentResult)

        val driftResult = TwoFactorAuthenticator.verifyTotpCode(secret, prevCode, nowMillis = nowMillis)
        assertEquals(TotpVerifyOutcome.Verified(baseStep - 1), driftResult)

        // Expired code outside +-1 step tolerance fails
        assertEquals(
            TotpVerifyOutcome.Mismatch,
            TwoFactorAuthenticator.verifyTotpCode(secret, expiredCode, nowMillis = nowMillis),
        )

        // Replay attack using same or older step is blocked once accepted
        assertEquals(
            TotpVerifyOutcome.CodeAlreadyUsed,
            TwoFactorAuthenticator.verifyTotpCode(
                secretBase32 = secret,
                candidateCode = currentCode,
                nowMillis = nowMillis,
                lastAcceptedStep = baseStep,
            ),
        )
        assertEquals(
            TotpVerifyOutcome.CodeAlreadyUsed,
            TwoFactorAuthenticator.verifyTotpCode(
                secretBase32 = secret,
                candidateCode = prevCode,
                nowMillis = nowMillis,
                lastAcceptedStep = baseStep,
            ),
        )

        // Non-6-digit formats rejected
        assertEquals(TotpVerifyOutcome.InvalidFormat, TwoFactorAuthenticator.verifyTotpCode(secret, "12345", nowMillis))
        assertEquals(TotpVerifyOutcome.InvalidFormat, TwoFactorAuthenticator.verifyTotpCode(secret, "12ab56", nowMillis))
    }

    @Test
    fun twoFactorFingerprintUriAndLockout_meetFirestoreRuleAndRateLimitContracts() {
        val secret = TwoFactorAuthenticator.generateSecretBase32()
        assertTrue(secret.length >= 32)

        val fingerprint = TwoFactorAuthenticator.sha256Hex(secret)
        assertTrue(fingerprint.matches(Regex("^[a-f0-9]{64}$")))

        val uri = TwoFactorAuthenticator.buildOtpAuthUri("dnzl0037@gmail.com", secret)
        assertTrue(uri.startsWith("otpauth://totp/Trolley:dnzl0037@gmail.com"))
        assertTrue(uri.contains("secret=$secret"))
        assertTrue(uri.contains("digits=6"))
        assertTrue(uri.contains("period=30"))

        val locked = LocalTwoFactorStatus(
            isEnrolled = true,
            secretFingerprint = fingerprint,
            failedAttempts = 0,
            lockedUntilEpochMs = 1_000_045_001L,
        )
        assertEquals(46, locked.remainingCooldownSeconds(nowMs = 1_000_000_000L))
        assertEquals(0, locked.remainingCooldownSeconds(nowMs = 1_000_045_001L))
    }

    @Test
    fun authManagerStepUpHelpers_normalizeTimestampsAndEnforceWindowExpiry() {
        // Both seconds and milliseconds normalize cleanly to epoch seconds
        assertEquals(1_760_000_000L, AuthManager.normalizeEpochSeconds(1_760_000_000L))
        assertEquals(1_760_000_000L, AuthManager.normalizeEpochSeconds(1_760_000_000_123L))
        assertEquals(0L, AuthManager.normalizeEpochSeconds(0L))

        val signInMs = 1_760_000_000_000L
        assertEquals(
            AuthManager.VERIFICATION_WINDOW_MS - 60_000L,
            AuthManager.computeRemainingVerificationMillis(signInMs, signInMs + 60_000L),
        )
        assertEquals(
            0L,
            AuthManager.computeRemainingVerificationMillis(signInMs, signInMs + AuthManager.VERIFICATION_WINDOW_MS + 1L),
        )
    }

    @Test
    fun chatValidatorAndThreadUnreadState_enforceDeterministicIdLengthAndParticipantIsolation() {
        val threadId = ChatValidator.deterministicThreadId(" prov_123 ", " cust_456 ")
        assertEquals("prov_123__cust_456", threadId)

        assertFalse(ChatValidator.isValidMessageBody("   "))
        assertTrue(ChatValidator.isValidMessageBody("Hi, are you available Friday?"))
        assertTrue(ChatValidator.isValidMessageBody("a".repeat(ChatValidator.MAX_MESSAGE_LENGTH)))
        assertFalse(ChatValidator.isValidMessageBody("a".repeat(ChatValidator.MAX_MESSAGE_LENGTH + 1)))

        assertEquals("pre_booking_inquiry", ChatValidator.resolveMessageType("pre_booking_inquiry"))
        assertEquals("text", ChatValidator.resolveMessageType("script_injection"))
        assertEquals(listOf("cust_456", "prov_123"), ChatValidator.buildParticipantIds("cust_456", "prov_123"))

        val t100 = com.google.firebase.Timestamp(1_760_000_100L, 0)
        val t200 = com.google.firebase.Timestamp(1_760_000_200L, 0)
        val threadFromCustomer = ChatThread(
            id = threadId,
            customerId = "cust_456",
            providerId = "prov_123",
            providerUserId = "prov_owner_99",
            participantIds = listOf("cust_456", "prov_owner_99"),
            customerName = "Tariro",
            providerBusinessName = "Harare Solar Pros",
            providerCategoryName = "Solar & Power",
            lastMessageText = "Quote please",
            lastSenderId = "cust_456",
            messageCount = 1,
            lastCustomerReadAt = t200,
            lastProviderReadAt = t100,
            createdAt = t100,
            updatedAt = t200,
        )

        assertFalse(threadFromCustomer.hasUnreadFor("cust_456"))
        assertTrue(threadFromCustomer.hasUnreadFor("prov_owner_99"))
        assertEquals("Harare Solar Pros", threadFromCustomer.partnerTitleFor("cust_456"))
        assertEquals("Tariro", threadFromCustomer.partnerTitleFor("prov_owner_99"))

        val threadMarkedReadByProvider = threadFromCustomer.copy(lastProviderReadAt = t200)
        assertFalse(threadMarkedReadByProvider.hasUnreadFor("prov_owner_99"))
    }
}

