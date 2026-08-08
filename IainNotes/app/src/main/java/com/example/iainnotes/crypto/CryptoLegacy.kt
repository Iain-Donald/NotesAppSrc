package com.example.iainnotes.crypto

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** v1 container format: [16 salt][12 iv][ciphertext+tag], PBKDF2-HMAC-SHA256 @ 205000, AES-256-GCM.
 *  DECRYPT ONLY. Delete this file once no v1 containers remain in the wild. */
internal object CryptoLegacy {
	private const val SALT_LENGTH = 16
	private const val IV_LENGTH = 12
	private const val GCM_TAG_BITS = 128
	private const val ITERATIONS = 205000

	private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKey {
		val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
		return SecretKeySpec(
			factory.generateSecret(PBEKeySpec(passphrase, salt, ITERATIONS, 256)).encoded,
			"AES"
		)
	}

	fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
		if (data.size < SALT_LENGTH + IV_LENGTH + GCM_TAG_BITS / 8)
			throw CryptoException("Legacy container is truncated or corrupt")
		val salt = data.copyOfRange(0, SALT_LENGTH)
		val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
		val ct = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)
		val cipher = Cipher.getInstance("AES/GCM/NoPadding")
		cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
		return cipher.doFinal(ct)
	}
}