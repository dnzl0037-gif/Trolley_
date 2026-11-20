package com.example.data.catalog

import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

enum class SubscriptionTier(
    val id: String,
    val title: String,
    val badge: String,
    val tagline: String,
    val monthlyCents: Int,
    val monthlyBookingLimit: Int,
    val maxCategories: Int,
    val features: List<String>,
) {
    FREE_STARTER(
        id = "free_starter",
        title = "Starter Free",
        badge = "4 Free Features · Limited",
        tagline = "Launch your business draft and test Trolley with clear monthly caps.",
        monthlyCents = 0,
        monthlyBookingLimit = 3,
        maxCategories = 1,
        features = listOf(
            "1 provider profile draft & standard listing slot",
            "Up to 3 incoming booking requests per month",
            "1 primary service category & 1 operating city",
            "Public customer star ratings & review feedback card",
        ),
    ),
    GROWTH_PRO(
        id = "growth_pro",
        title = "Growth Pro",
        badge = "Popular for Pros",
        tagline = "Unlock unlimited bookings and expand into 3 service categories.",
        monthlyCents = 499,
        monthlyBookingLimit = 9999,
        maxCategories = 3,
        features = listOf(
            "Unlimited incoming booking requests every month",
            "Up to 3 service categories & multi-city discovery",
            "Priority business map pin & instant schedule alerts",
            "Verified invoice receipts & rating breakdown insights",
        ),
    ),
    BUSINESS_ELITE(
        id = "business_elite",
        title = "Business Elite",
        badge = "Best Value · Top Tier",
        tagline = "Maximum reach at $9.99/mo with 30% annual savings and full category scale.",
        monthlyCents = 999,
        monthlyBookingLimit = 9999,
        maxCategories = 10,
        features = listOf(
            "Unlimited bookings with priority scheduling slots",
            "Up to 10 service categories across regional markets",
            "Featured spotlight placement in category & map search",
            "Multi-rail billing ledger (EcoCash, Visa, Mastercard & more)",
        ),
    );

    companion object {
        fun fromId(id: String): SubscriptionTier? = entries.firstOrNull { it.id == id }
    }
}

enum class BillingCycle(val id: String, val label: String) {
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly (Save 30%)");

    companion object {
        fun fromId(id: String): BillingCycle? = entries.firstOrNull { it.id == id }
    }
}

enum class PaymentMethodType(
    val id: String,
    val displayName: String,
    val groupLabel: String,
    val supportsAutoRenew: Boolean,
) {
    ECOCASH("ecocash", "EcoCash", "Mobile Money (Zimbabwe)", supportsAutoRenew = true),
    VISA("visa", "Visa", "Card Network", supportsAutoRenew = true),
    MASTERCARD("mastercard", "Mastercard", "Card Network", supportsAutoRenew = true),
    INNBUCKS("innbucks", "InnBucks", "Other Billing Systems", supportsAutoRenew = false),
    PAYPAL("paypal", "PayPal", "Other Billing Systems", supportsAutoRenew = true),
    BANK_EFT("bank_eft", "Bank Transfer (EFT)", "Other Billing Systems", supportsAutoRenew = false),
    NONE("none", "No payment method (Free Tier)", "Free Plan", supportsAutoRenew = false);

    companion object {
        fun fromId(id: String): PaymentMethodType? = entries.firstOrNull { it.id == id }

        val featuredMethods: List<PaymentMethodType> = listOf(ECOCASH, VISA, MASTERCARD)
        val otherMethods: List<PaymentMethodType> = listOf(INNBUCKS, PAYPAL, BANK_EFT)
        val allPaidMethods: List<PaymentMethodType> = featuredMethods + otherMethods
    }
}

data class BillingQuote(
    val tier: SubscriptionTier,
    val cycle: BillingCycle,
    val subtotalCents: Int,
    val discountCents: Int,
    val amountCents: Int,
    val savingsPercent: Int,
) {
    val formattedPrice: String get() = formatUsdCents(amountCents)
    val formattedSubtotal: String get() = formatUsdCents(subtotalCents)
    val formattedSavings: String get() = formatUsdCents(discountCents)
}

object BillingCatalog {
    const val ANNUAL_DISCOUNT_PERCENT = 30
    const val CURRENCY_USD = "USD"

    fun computeQuote(tier: SubscriptionTier, cycle: BillingCycle): BillingQuote {
        if (tier.monthlyCents == 0) {
            return BillingQuote(
                tier = tier,
                cycle = cycle,
                subtotalCents = 0,
                discountCents = 0,
                amountCents = 0,
                savingsPercent = 0,
            )
        }
        return when (cycle) {
            BillingCycle.MONTHLY -> BillingQuote(
                tier = tier,
                cycle = cycle,
                subtotalCents = tier.monthlyCents,
                discountCents = 0,
                amountCents = tier.monthlyCents,
                savingsPercent = 0,
            )
            BillingCycle.YEARLY -> {
                // e.g. $9.99 * 12 = $119.88 (11988 cents) -> 30% discount = 3596 cents -> $83.92 (8392 cents)
                // e.g. $4.99 * 12 = $59.88 (5988 cents) -> 30% discount = 1796 cents -> $41.92 (4192 cents)
                val subtotal = tier.monthlyCents * 12
                val discount = (subtotal * 0.30).roundToInt()
                val finalAmount = subtotal - discount
                BillingQuote(
                    tier = tier,
                    cycle = cycle,
                    subtotalCents = subtotal,
                    discountCents = discount,
                    amountCents = finalAmount,
                    savingsPercent = ANNUAL_DISCOUNT_PERCENT,
                )
            }
        }
    }

    fun generateInvoiceNumber(userId: String, nowEpochSec: Long = System.currentTimeMillis() / 1000L): String {
        val safeUidSuffix = userId.filter { it.isLetterOrDigit() }.uppercase(Locale.ENGLISH).take(6).ifEmpty { "PRO" }
        val tail = (nowEpochSec % 1_000_000L).toString().padStart(6, '0')
        return "TRL-$safeUidSuffix-$tail"
    }
}

fun formatUsdCents(cents: Int): String {
    val safeCents = cents.coerceAtLeast(0)
    val dollars = safeCents / 100
    val remainder = safeCents % 100
    return String.format(Locale.US, "$%d.%02d", dollars, remainder)
}

data class PaymentValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val maskedInstrument: String = "",
    val subscriptionStatus: String = "active",
    val paymentStatus: String = "authorized",
)

/**
 * Validates EcoCash, Visa, Mastercard, InnBucks, PayPal, and Bank EFT checkout inputs locally
 * and produces strictly PCI/PII-safe masked tokens for Firestore persistence.
 * Raw card PANs, CVVs, and mobile PINs are NEVER stored.
 */
object PaymentValidator {
    private val VISA_REGEX = Regex("^4[0-9]{12}(?:[0-9]{3})?(?:[0-9]{3})?$")
    private val MASTERCARD_REGEX = Regex("^(?:5[1-5][0-9]{14}|2(?:2[2-9][1-9]|2[3-9][0-9]|[3-6][0-9]{2}|7[01][0-9]|720)[0-9]{12})$")
    private val ECOCASH_ZW_REGEX = Regex("^(?:\\+?263|0)7[78][0-9]{7}$")
    private val INNBUCKS_PHONE_REGEX = Regex("^(?:\\+?263|0)7[1378][0-9]{7}$")
    private val EXPIRY_REGEX = Regex("^(0[1-9]|1[0-2])/([0-9]{2})$")
    private val CVC_REGEX = Regex("^[0-9]{3,4}$")
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val BANK_REF_REGEX = Regex("^[A-Za-z0-9\\s\\-]{4,24}$")
    private val UNSAFE_DIGIT_RUN_REGEX = Regex(".*[0-9]{6,}.*")

    fun normalizeDigits(input: String): String = input.filter { it.isDigit() }

    fun passesLuhnCheck(digitsOnly: String): Boolean {
        if (digitsOnly.length !in 13..19 || !digitsOnly.all { it.isDigit() }) return false
        var sum = 0
        var alternate = false
        for (i in digitsOnly.length - 1 downTo 0) {
            var n = digitsOnly[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    fun isValidExpiry(mmYy: String, currentMonth: YearMonth = YearMonth.now()): Boolean {
        val match = EXPIRY_REGEX.matchEntire(mmYy.trim()) ?: return false
        val month = match.groupValues[1].toInt()
        val year = 2000 + match.groupValues[2].toInt()
        val cardMonth = runCatching { YearMonth.of(year, month) }.getOrNull() ?: return false
        return !cardMonth.isBefore(currentMonth)
    }

    fun isSafeMaskedInstrument(masked: String): Boolean {
        val trimmed = masked.trim()
        return trimmed.length in 3..80 && !UNSAFE_DIGIT_RUN_REGEX.matches(trimmed)
    }

    fun validateAndMask(
        tier: SubscriptionTier,
        method: PaymentMethodType,
        mobileNumber: String = "",
        cardholderName: String = "",
        cardNumber: String = "",
        cardExpiry: String = "",
        cardCvc: String = "",
        paypalEmail: String = "",
        bankAccountLast4OrRef: String = "",
        currentMonth: YearMonth = YearMonth.now(),
    ): PaymentValidationResult {
        if (tier == SubscriptionTier.FREE_STARTER) {
            return PaymentValidationResult(
                isValid = true,
                maskedInstrument = "Free Tier",
                subscriptionStatus = "active",
                paymentStatus = "free_activated",
            )
        }

        if (method == PaymentMethodType.NONE) {
            return PaymentValidationResult(
                isValid = false,
                errorMessage = "Select EcoCash, Visa, Mastercard, or another billing method for paid plans.",
            )
        }

        return when (method) {
            PaymentMethodType.ECOCASH -> {
                val cleaned = mobileNumber.trim().replace(" ", "").replace("-", "")
                if (!ECOCASH_ZW_REGEX.matches(cleaned)) {
                    PaymentValidationResult(
                        isValid = false,
                        errorMessage = "Enter a valid Zimbabwe EcoCash number starting with 077, 078, or +26377/78.",
                    )
                } else {
                    val last3 = cleaned.takeLast(3)
                    val prefix = if (cleaned.contains("78")) "78" else "77"
                    PaymentValidationResult(
                        isValid = true,
                        maskedInstrument = "EcoCash +263 $prefix***$last3",
                        subscriptionStatus = "pending_confirmation",
                        paymentStatus = "pending_gateway",
                    )
                }
            }

            PaymentMethodType.INNBUCKS -> {
                val cleaned = mobileNumber.trim().replace(" ", "").replace("-", "")
                if (!INNBUCKS_PHONE_REGEX.matches(cleaned)) {
                    PaymentValidationResult(
                        isValid = false,
                        errorMessage = "Enter a valid Zimbabwe InnBucks wallet mobile number (071/073/077/078).",
                    )
                } else {
                    val last3 = cleaned.takeLast(3)
                    PaymentValidationResult(
                        isValid = true,
                        maskedInstrument = "InnBucks +263 ***$last3",
                        subscriptionStatus = "pending_confirmation",
                        paymentStatus = "pending_gateway",
                    )
                }
            }

            PaymentMethodType.VISA -> validateCard(
                expectedBrand = "Visa",
                binRegex = VISA_REGEX,
                cardholderName = cardholderName,
                cardNumber = cardNumber,
                cardExpiry = cardExpiry,
                cardCvc = cardCvc,
                currentMonth = currentMonth,
            )

            PaymentMethodType.MASTERCARD -> validateCard(
                expectedBrand = "Mastercard",
                binRegex = MASTERCARD_REGEX,
                cardholderName = cardholderName,
                cardNumber = cardNumber,
                cardExpiry = cardExpiry,
                cardCvc = cardCvc,
                currentMonth = currentMonth,
            )

            PaymentMethodType.PAYPAL -> {
                val cleanEmail = paypalEmail.trim()
                if (!EMAIL_REGEX.matches(cleanEmail) || cleanEmail.length > 120) {
                    PaymentValidationResult(
                        isValid = false,
                        errorMessage = "Enter a valid PayPal account email address.",
                    )
                } else {
                    val parts = cleanEmail.split("@")
                    val maskedLocal = parts[0].take(1) + "***"
                    val masked = "PayPal $maskedLocal@${parts[1]}".take(80)
                    PaymentValidationResult(
                        isValid = true,
                        maskedInstrument = masked,
                        subscriptionStatus = "active",
                        paymentStatus = "authorized",
                    )
                }
            }

            PaymentMethodType.BANK_EFT -> {
                val cleanRef = bankAccountLast4OrRef.trim()
                val digitCount = cleanRef.count { it.isDigit() }
                if (!BANK_REF_REGEX.matches(cleanRef) || digitCount > 4) {
                    PaymentValidationResult(
                        isValid = false,
                        errorMessage = "Enter a short bank name or account suffix (last 4 digits max, never full account number).",
                    )
                } else {
                    val tail4 = cleanRef.filter { it.isLetterOrDigit() }.takeLast(4).uppercase(Locale.ENGLISH)
                    PaymentValidationResult(
                        isValid = true,
                        maskedInstrument = "Bank EFT •••• $tail4",
                        subscriptionStatus = "pending_confirmation",
                        paymentStatus = "pending_gateway",
                    )
                }
            }

            PaymentMethodType.NONE -> PaymentValidationResult(
                isValid = false,
                errorMessage = "Choose a payment method.",
            )
        }
    }

    private fun validateCard(
        expectedBrand: String,
        binRegex: Regex,
        cardholderName: String,
        cardNumber: String,
        cardExpiry: String,
        cardCvc: String,
        currentMonth: YearMonth,
    ): PaymentValidationResult {
        if (cardholderName.trim().length !in 2..80) {
            return PaymentValidationResult(false, "Enter the cardholder's full name.")
        }
        val digits = normalizeDigits(cardNumber)
        if (!binRegex.matches(digits)) {
            return PaymentValidationResult(
                isValid = false,
                errorMessage = if (expectedBrand == "Visa") {
                    "Visa card numbers start with 4 and have 13, 16, or 19 digits."
                } else {
                    "Mastercard numbers start with 51–55 or 2221–2720 and have 16 digits."
                },
            )
        }
        if (!passesLuhnCheck(digits)) {
            return PaymentValidationResult(false, "Card number failed checksum verification. Check the digits.")
        }
        if (!isValidExpiry(cardExpiry, currentMonth)) {
            return PaymentValidationResult(false, "Enter a valid future expiry date in MM/YY format.")
        }
        val cleanCvc = cardCvc.trim()
        if (!CVC_REGEX.matches(cleanCvc)) {
            return PaymentValidationResult(false, "Enter a 3- or 4-digit security code (CVC).")
        }
        val masked = "$expectedBrand •••• ${digits.takeLast(4)}"
        return PaymentValidationResult(
            isValid = isSafeMaskedInstrument(masked),
            maskedInstrument = masked,
            subscriptionStatus = "active",
            paymentStatus = "authorized",
        )
    }
}
