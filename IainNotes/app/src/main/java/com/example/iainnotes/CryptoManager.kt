package com.example.iainnotes

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_LENGTH_BITS = 256

    // WARNING! Don't change these values unless the app has no password set up.
    private const val PBKDF2_ITERATIONS = 205000 // 105000 // <-- testing // 425000 <-- better after multi-threading // 600000 <-- official recommended default in 2023 // 645000 <-- SV650 CCs x1000
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return salt + iv + ciphertext
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        val salt = data.sliceArray(0 until SALT_LENGTH)
        val iv = data.sliceArray(SALT_LENGTH until SALT_LENGTH + IV_LENGTH)
        val ciphertext = data.sliceArray(SALT_LENGTH + IV_LENGTH until data.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}