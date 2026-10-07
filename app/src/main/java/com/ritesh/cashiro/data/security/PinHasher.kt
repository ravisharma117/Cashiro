package com.ritesh.cashiro.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted, slow hashing of the app PIN. The PIN itself is never stored or logged.
 *
 * A 4-digit PIN has only 10,000 possibilities, so the hash cannot stand up to someone
 * who has copied it off the phone. What protects it is that it lives in encrypted
 * storage and that [PinThrottle] slows guessing on the phone. The slow hash is a second layer.
 */
object PinHasher {
    const val PIN_LENGTH = 4
    const val ITERATIONS = 150_000

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time comparison, so timing does not reveal how much of a guess was right. */
    fun matches(pin: String, salt: ByteArray, expected: ByteArray, iterations: Int = ITERATIONS): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations), expected)

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}
