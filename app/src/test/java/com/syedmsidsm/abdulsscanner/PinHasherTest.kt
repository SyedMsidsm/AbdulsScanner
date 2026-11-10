package com.syedmsidsm.abdulsscanner

import com.syedmsidsm.abdulsscanner.domain.security.PinHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun samePinAndSaltProduceSameHash() {
        val salt = PinHasher.generateSalt()
        val pin = "1234"

        val hash1 = PinHasher.hashPin(pin, salt)
        val hash2 = PinHasher.hashPin(pin, salt)

        assertEquals(hash1, hash2)
        assertTrue(PinHasher.verifyPin(pin, salt, hash1))
    }

    @Test
    fun differentSaltProducesDifferentHash() {
        val salt1 = PinHasher.generateSalt()
        val salt2 = PinHasher.generateSalt()
        val pin = "1234"

        val hash1 = PinHasher.hashPin(pin, salt1)
        val hash2 = PinHasher.hashPin(pin, salt2)

        assertNotEquals(hash1, hash2)
        assertFalse(PinHasher.verifyPin(pin, salt2, hash1))
    }

    @Test
    fun wrongPinFailsVerification() {
        val salt = PinHasher.generateSalt()
        val correctPin = "4567"
        val wrongPin = "7654"

        val hash = PinHasher.hashPin(correctPin, salt)
        assertFalse(PinHasher.verifyPin(wrongPin, salt, hash))
    }

    @Test
    fun saltBase64RoundTrip() {
        val salt = PinHasher.generateSalt()
        val base64 = PinHasher.saltToBase64(salt)
        val decoded = PinHasher.base64ToSalt(base64)

        assertEquals(salt.toList(), decoded.toList())
    }
}
