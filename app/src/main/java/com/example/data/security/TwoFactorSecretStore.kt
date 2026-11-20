package com.example.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class LocalTwoFactorStatus(
    val isEnrolled: Boolean = false,
    val secretFingerprint: String? = null,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long = 0L,
    val lastAcceptedStep: Long? = null,
) {
    fun remainingCooldownSeconds(nowMs: Long = System.currentTimeMillis()): Int {
        val diff = lockedUntilEpochMs - nowMs
        return if (diff > 0L) ((diff + 999L) / 1000L).toInt() else 0
    }
}

/**
 * Seals each provider's Base32 TOTP secret at rest using Android Keystore AES/GCM/NoPadding
 * so plaintext 2FA secrets are never written to SharedPreferences or Firestore.
 */
class TwoFactorSecretStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getOrCreateAesKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptSecret(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateAesKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encrypted.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptSecret(encoded: String): String? = runCatching {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLength = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val cipherText = combined.copyOfRange(1 + ivLength, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateAesKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()

    fun saveEnrolledSecret(userId: String, secretBase32: String): String {
        val cleanSecret = secretBase32.trim()
        val sealed = encryptSecret(cleanSecret)
        val fingerprint = TwoFactorAuthenticator.sha256Hex(cleanSecret)
        prefs.edit()
            .putString(keySecret(userId), sealed)
            .putString(keyFingerprint(userId), fingerprint)
            .putInt(keyFailures(userId), 0)
            .putLong(keyLockUntil(userId), 0L)
            .remove(keyLastStep(userId))
            .apply()
        return fingerprint
    }

    fun loadDecryptedSecret(userId: String): String? {
        val sealed = prefs.getString(keySecret(userId), null) ?: return null
        return decryptSecret(sealed)
    }

    fun getStatus(userId: String): LocalTwoFactorStatus {
        val hasSecret = prefs.contains(keySecret(userId)) && loadDecryptedSecret(userId) != null
        val fingerprint = prefs.getString(keyFingerprint(userId), null)
        val failures = prefs.getInt(keyFailures(userId), 0)
        val lockUntil = prefs.getLong(keyLockUntil(userId), 0L)
        val lastStep = if (prefs.contains(keyLastStep(userId))) {
            prefs.getLong(keyLastStep(userId), -1L).takeIf { it >= 0L }
        } else {
            null
        }
        return LocalTwoFactorStatus(
            isEnrolled = hasSecret,
            secretFingerprint = fingerprint,
            failedAttempts = failures,
            lockedUntilEpochMs = lockUntil,
            lastAcceptedStep = lastStep,
        )
    }

    fun recordVerificationAttempt(
        userId: String,
        code: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Pair<TotpVerifyOutcome?, LocalTwoFactorStatus> {
        val current = getStatus(userId)
        if (!current.isEnrolled) {
            return null to current
        }
        if (current.remainingCooldownSeconds(nowMs) > 0) {
            return null to current
        }
        val secret = loadDecryptedSecret(userId) ?: return null to current
        val outcome = TwoFactorAuthenticator.verifyTotpCode(
            secretBase32 = secret,
            candidateCode = code,
            nowMillis = nowMs,
            lastAcceptedStep = current.lastAcceptedStep,
        )
        when (outcome) {
            is TotpVerifyOutcome.Verified -> {
                prefs.edit()
                    .putInt(keyFailures(userId), 0)
                    .putLong(keyLockUntil(userId), 0L)
                    .putLong(keyLastStep(userId), outcome.matchedStep)
                    .apply()
            }
            TotpVerifyOutcome.Mismatch -> {
                val nextFailures = current.failedAttempts + 1
                val editor = prefs.edit().putInt(keyFailures(userId), nextFailures)
                if (nextFailures >= TwoFactorAuthenticator.MAX_FAILED_ATTEMPTS) {
                    editor.putLong(keyLockUntil(userId), nowMs + TwoFactorAuthenticator.COOLDOWN_MILLIS)
                        .putInt(keyFailures(userId), 0)
                }
                editor.apply()
            }
            TotpVerifyOutcome.CodeAlreadyUsed,
            TotpVerifyOutcome.InvalidFormat -> Unit
        }
        return outcome to getStatus(userId)
    }

    fun clearEnrollment(userId: String) {
        prefs.edit()
            .remove(keySecret(userId))
            .remove(keyFingerprint(userId))
            .remove(keyFailures(userId))
            .remove(keyLockUntil(userId))
            .remove(keyLastStep(userId))
            .apply()
    }

    private fun keySecret(uid: String) = "sealed_totp_$uid"
    private fun keyFingerprint(uid: String) = "fp_totp_$uid"
    private fun keyFailures(uid: String) = "fails_totp_$uid"
    private fun keyLockUntil(uid: String) = "lock_totp_$uid"
    private fun keyLastStep(uid: String) = "step_totp_$uid"

    private companion object {
        const val PREFS_NAME = "trolley_2fa_keystore_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "trolley_provider_2fa_aes_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
