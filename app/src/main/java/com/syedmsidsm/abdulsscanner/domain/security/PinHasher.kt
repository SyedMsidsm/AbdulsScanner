package com.syedmsidsm.abdulsscanner.domain.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    const val ITERATIONS = 120_000
    const val KEY_LENGTH = 256
    const val ALGORITHM = "PBKDF2WithHmacSHA256"

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return salt
    }

    fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verifyPin(pin: String, salt: ByteArray, expectedHash: String): Boolean {
        val hash = hashPin(pin, salt)
        return hash == expectedHash
    }

    fun saltToBase64(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)
    fun base64ToSalt(base64: String): ByteArray = Base64.getDecoder().decode(base64)
}
