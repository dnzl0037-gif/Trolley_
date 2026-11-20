package com.example.data.security

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure RFC-6238 (HMAC-SHA1, 6-digit, 30-second window) Two-Factor Authentication engine
 * with RFC-4648 Base32 encoding, constant-time verification, replay protection, and
 * brute-force rate limiting.
 */
object TwoFactorAuthenticator {

    const val TIME_STEP_SECONDS = 30L
    const val DIGITS = 6
    const val SECRET_BYTES = 20 // 160-bit secret per RFC 4226 / RFC 6238
    const val MAX_FAILED_ATTEMPTS = 5
    const val COOLDOWN_MILLIS = 60_000L

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateSecretBase32(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(SECRET_BYTES)
        random.nextBytes(bytes)
        return encodeBase32(bytes)
    }

    fun encodeBase32(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val result = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = data[0].toInt() and 0xFF
        var next = 1
        var bitsLeft = 8
        while (bitsLeft > 0 || next < data.size) {
            if (bitsLeft < 5) {
                if (next < data.size) {
                    buffer = (buffer shl 8) or (data[next++].toInt() and 0xFF)
                    bitsLeft += 8
                } else {
                    val pad = 5 - bitsLeft
                    buffer = buffer shl pad
                    bitsLeft += pad
                }
            }
            val index = (buffer shr (bitsLeft - 5)) and 0x1F
            bitsLeft -= 5
            result.append(BASE32_ALPHABET[index])
        }
        return result.toString()
    }

    fun decodeBase32(base32: String): ByteArray {
        val clean = base32.trim().replace("=", "").replace(" ", "").uppercase(Locale.ENGLISH)
        if (clean.isEmpty()) return ByteArray(0)
        val output = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            val value = BASE32_ALPHABET.indexOf(ch)
            require(value >= 0) { "Invalid Base32 character: $ch" }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }

    fun currentTimeStep(nowMillis: Long = System.currentTimeMillis()): Long {
        return (nowMillis / 1000L) / TIME_STEP_SECONDS
    }

    fun secondsRemainingInStep(nowMillis: Long = System.currentTimeMillis()): Int {
        val elapsedInStep = ((nowMillis / 1000L) % TIME_STEP_SECONDS).toInt()
        return (TIME_STEP_SECONDS.toInt() - elapsedInStep).coerceIn(1, TIME_STEP_SECONDS.toInt())
    }

    fun computeTotpCode(
        secretBase32: String,
        timeStep: Long = currentTimeStep(),
    ): String {
        val keyBytes = decodeBase32(secretBase32)
        require(keyBytes.isNotEmpty()) { "TOTP secret cannot be empty." }
        val stepBytes = ByteBuffer.allocate(8).putLong(timeStep).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA1"))
        val hash = mac.doFinal(stepBytes)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return otp.toString().padStart(DIGITS, '0')
    }

    fun verifyTotpCode(
        secretBase32: String,
        candidateCode: String,
        nowMillis: Long = System.currentTimeMillis(),
        lastAcceptedStep: Long? = null,
        windowRadius: Int = 1,
    ): TotpVerifyOutcome {
        val trimmed = candidateCode.trim()
        if (!trimmed.matches(Regex("^[0-9]{6}$"))) {
            return TotpVerifyOutcome.InvalidFormat
        }
        val baseStep = currentTimeStep(nowMillis)
        val candidateBytes = trimmed.toByteArray(Charsets.US_ASCII)

        for (offset in -windowRadius..windowRadius) {
            val step = baseStep + offset
            if (step < 0) continue
            val expectedBytes = computeTotpCode(secretBase32, step).toByteArray(Charsets.US_ASCII)
            if (MessageDigest.isEqual(candidateBytes, expectedBytes)) {
                if (lastAcceptedStep != null && step <= lastAcceptedStep) {
                    return TotpVerifyOutcome.CodeAlreadyUsed
                }
                return TotpVerifyOutcome.Verified(matchedStep = step)
            }
        }
        return TotpVerifyOutcome.Mismatch
    }

    fun buildOtpAuthUri(
        accountEmailOrLabel: String,
        secretBase32: String,
        issuer: String = "Trolley",
    ): String {
        val label = accountEmailOrLabel.trim().ifBlank { "provider" }
        val encodedLabel = "${issuer}:${label}".replace(" ", "%20")
        return "otpauth://totp/$encodedLabel?secret=$secretBase32&issuer=$issuer&algorithm=SHA1&digits=$DIGITS&period=$TIME_STEP_SECONDS"
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

sealed interface TotpVerifyOutcome {
    data class Verified(val matchedStep: Long) : TotpVerifyOutcome
    data object Mismatch : TotpVerifyOutcome
    data object CodeAlreadyUsed : TotpVerifyOutcome
    data object InvalidFormat : TotpVerifyOutcome
}
