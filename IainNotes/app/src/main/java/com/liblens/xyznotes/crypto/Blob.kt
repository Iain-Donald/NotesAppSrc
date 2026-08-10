package com.liblens.xyznotes.crypto

internal object Blob {

	/** AAD = the blob's logical path. Binds ciphertext to location, so a file
	 *  surviving a rename cannot be reinterpreted as a different note. */
	fun seal(plaintext: ByteArray, path: String, dataKey: ByteArray?, encrypted: Boolean): ByteArray {
		// 192-bit random nonce, fresh per write. No counter is kept: at this size
		// random selection is collision-free for any realistic number of writes,
		// which is why a restore-from-backup cannot cause nonce reuse the way a
		// persisted counter could. Uniqueness is required per-key, and every
		// install generates its own DK.
		val header = ByteArray(Format.BLOB_HEADER_LEN)
		Format.MAGIC.copyInto(header, 0)
		Format.putU16(header, 4, Format.VERSION)
		header[6] = (if (encrypted) Format.AEAD_XCHACHA else Format.AEAD_NONE).toByte()
		header[7] = 0

		if (!encrypted) return header + plaintext

		requireNotNull(dataKey) { "encrypted blob requires a data key" }
		val nonce = ByteArray(Sodium.NONCE_BYTES).also { Sodium.randomBytes(it) }
		nonce.copyInto(header, 8)

		val ct = Sodium.aeadEncrypt(plaintext, path.toByteArray(Charsets.UTF_8), nonce, dataKey)
			?: throw CryptoException("Encryption failed for $path")
		return header + ct
	}

	fun open(blob: ByteArray, path: String, dataKey: ByteArray?): ByteArray {
		if (blob.size < Format.BLOB_HEADER_LEN || !Format.hasMagic(blob))
			throw CryptoException("Not a valid blob: $path")

		val version = Format.getU16(blob, 4)
		if (version > Format.VERSION)
			throw CryptoException("Blob $path was written by a newer version ($version)")

		val body = blob.copyOfRange(Format.BLOB_HEADER_LEN, blob.size)

		return when (blob[6].toInt()) {
			Format.AEAD_NONE -> body
			Format.AEAD_XCHACHA -> {
				requireNotNull(dataKey) { "encrypted blob requires a data key" }
				val nonce = blob.copyOfRange(8, 8 + Sodium.NONCE_BYTES)
				Sodium.aeadDecrypt(body, path.toByteArray(Charsets.UTF_8), nonce, dataKey)
					?: throw CryptoException("Authentication failed for $path — corrupt or tampered")
			}
			else -> throw CryptoException("Unknown AEAD id in $path")
		}
	}

	fun isEncrypted(blob: ByteArray): Boolean =
		blob.size > 6 && blob[6].toInt() == Format.AEAD_XCHACHA
}