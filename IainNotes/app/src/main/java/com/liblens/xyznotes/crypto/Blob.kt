package com.liblens.xyznotes.crypto

internal object Blob {

    /** AAD = header prefix ‖ logical path.
     *
     *  Two properties, and both matter:
     *
     *  The path binds ciphertext to location, so a file surviving a rename
     *  cannot be reinterpreted as a different note.
     *
     *  The header prefix binds the format bytes — critically the AEAD id. With
     *  the path alone, flipping byte 6 from AEAD_XCHACHA to AEAD_NONE made
     *  open() return the ciphertext as plaintext with no authentication run at
     *  all, and the app would then treat that as note content. Covering the
     *  prefix turns that downgrade into an authentication failure, which is the
     *  same discipline KeyFile already applies to its Argon2 parameters.
     *
     *  The path is NOT vault-qualified. Vault scoping comes from the data key —
     *  each vault has its own — so including a vault id here would add nothing
     *  and would make a vault rename invalidate every blob in it. */
    private fun aadFor(header: ByteArray, path: String): ByteArray =
        header.copyOfRange(0, Format.BLOB_AAD_PREFIX) + path.toByteArray(Charsets.UTF_8)

    fun seal(plaintext: ByteArray, path: String, dataKey: ByteArray?, encrypted: Boolean): ByteArray {
        // 192-bit random nonce, fresh per write. No counter is kept: at this size
        // random selection is collision-free for any realistic number of writes,
        // which is why a restore-from-backup cannot cause nonce reuse the way a
        // persisted counter could. Uniqueness is required per-key, and every
        // vault generates its own DK.
        val header = ByteArray(Format.BLOB_HEADER_LEN)
        Format.MAGIC.copyInto(header, 0)
        Format.putU16(header, 4, Format.VERSION)
        header[6] = (if (encrypted) Format.AEAD_XCHACHA else Format.AEAD_NONE).toByte()
        header[7] = 0

        if (!encrypted) return header + plaintext

        requireNotNull(dataKey) { "encrypted blob requires a data key" }
        val nonce = ByteArray(Sodium.NONCE_BYTES).also { Sodium.randomBytes(it) }
        nonce.copyInto(header, 8)

        val ct = Sodium.aeadEncrypt(plaintext, aadFor(header, path), nonce, dataKey)
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
                Sodium.aeadDecrypt(body, aadFor(blob, path), nonce, dataKey)
                    ?: throw CryptoException("Authentication failed for $path — corrupt or tampered")
            }
            else -> throw CryptoException("Unknown AEAD id in $path")
        }
    }

    fun isEncrypted(blob: ByteArray): Boolean =
        blob.size > 6 && blob[6].toInt() == Format.AEAD_XCHACHA
}