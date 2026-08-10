package com.liblens.xyznotes.crypto

import android.os.Environment
import java.io.File
import com.liblens.xyznotes.BuildConfig

internal object KeyFile {

	/** Three copies. Losing all of them means losing the data key permanently,
	 *  so redundancy here is disproportionately valuable — 108 bytes each. */
	fun locations(): List<File> {
		/*val root = Environment.getExternalStorageDirectory()
		return listOf(
			File(root, "IainNotes/keys.xync"),
			File(root, "IainNotes/keys.xync.bak"),
			File(root, "IainNotes/userData/.keys.xync")
		)*/
		val root = Environment.getExternalStorageDirectory()
		return listOf(
			File(root, "${BuildConfig.DATA_DIR}/keys.xync"),
			File(root, "${BuildConfig.DATA_DIR}/keys.xync.bak"),
			File(root, "${BuildConfig.DATA_DIR}/userData/.keys.xync")
		)
	}

	fun exists(): Boolean = locations().any { it.exists() }

	/** Creates a fresh random data key, wraps it, writes all copies. Returns the DK. */
	fun create(passphrase: CharArray?): ByteArray {
		val dataKey = ByteArray(Sodium.KEY_BYTES).also { Sodium.randomBytes(it) }
		write(dataKey, passphrase)
		return dataKey
	}

	fun write(dataKey: ByteArray, passphrase: CharArray?) {
		val encrypted = passphrase != null && passphrase.isNotEmpty()
		val out = ByteArray(Format.KEYFILE_LEN)

		Format.MAGIC.copyInto(out, 0)
		Format.putU16(out, 4, Format.VERSION)
		out[6] = (if (encrypted) Format.KDF_ARGON2ID else Format.KDF_NONE).toByte()
		out[7] = (if (encrypted) Format.AEAD_XCHACHA else Format.AEAD_NONE).toByte()
		Format.putU32(out, 8, if (encrypted) Format.ARGON_M_KIB else 0L)
		Format.putU32(out, 12, if (encrypted) Format.ARGON_T else 0L)
		Format.putU32(out, 16, if (encrypted) Format.ARGON_P else 0L)

		if (!encrypted) {
			// Plaintext mode: DK stored in the clear. Same structure, so enabling a
			// passphrase later is a rewrap, not a format migration.
			dataKey.copyInto(out, 60)
			AtomicFile.writeRedundant(locations(), out)
			return
		}

		val salt = ByteArray(Sodium.SALT_BYTES).also { Sodium.randomBytes(it) }
		salt.copyInto(out, 20)

		val kek = deriveKek(passphrase!!, salt)
		try {
			val nonce = ByteArray(Sodium.NONCE_BYTES).also { Sodium.randomBytes(it) }
			nonce.copyInto(out, 36)

			// AAD = header prefix [0,36). Downgrading params fails auth, not silently applies.
			val aad = out.copyOfRange(0, 36)
			val wrapped = Sodium.aeadEncrypt(dataKey, aad, nonce, kek)
				?: throw CryptoException("Failed to wrap data key")
			wrapped.copyInto(out, 60)   // 32 ciphertext + 16 tag
		} finally {
			Sodium.memzero(kek)
		}
		AtomicFile.writeRedundant(locations(), out)
	}

	/** Returns the unwrapped data key, or null if the passphrase is wrong. */
	fun unwrap(passphrase: CharArray?): ByteArray? {
		val raw = AtomicFile.readFirstValid(locations(), Format.KEYFILE_LEN)
			?: throw CryptoException("Key file missing or unreadable — data cannot be decrypted")

		val version = Format.getU16(raw, 4)
		if (version > Format.VERSION)
			throw CryptoException("Key file written by a newer version ($version)")

		val kdfId = raw[6].toInt()
		if (kdfId == Format.KDF_NONE) return raw.copyOfRange(60, 60 + Sodium.KEY_BYTES)
		if (kdfId != Format.KDF_ARGON2ID) throw CryptoException("Unknown KDF id $kdfId")
		if (passphrase == null || passphrase.isEmpty()) return null

		val mKib = Format.getU32(raw, 8)
		val t = Format.getU32(raw, 12)
		val salt = raw.copyOfRange(20, 20 + Sodium.SALT_BYTES)
		val nonce = raw.copyOfRange(36, 36 + Sodium.NONCE_BYTES)
		val wrapped = raw.copyOfRange(60, Format.KEYFILE_LEN)
		val aad = raw.copyOfRange(0, 36) // critical for metadata manipulation to weaken argon2, corruption detection, rejects blobs moved from their location as encrypted.

		val kek = deriveKek(passphrase, salt, mKib, t)
		try {
			return Sodium.aeadDecrypt(wrapped, aad, nonce, kek)   // null = wrong passphrase
		} finally {
			Sodium.memzero(kek)
		}
	}

	/** O(1) rewrap. Valid only when the old DK was never exposed in cleartext.
	 *  Enabling a passphrase from plaintext mode must go through rotation instead. */
	fun rewrap(oldPassphrase: CharArray?, newPassphrase: CharArray?): Boolean {
		val wasPlaintext = currentKdfId() == Format.KDF_NONE
		val willEncrypt = newPassphrase != null && newPassphrase.isNotEmpty()
		check(!(wasPlaintext && willEncrypt)) {
			"Cannot rewrap a plaintext-stored data key under a passphrase — rotate instead"
		}
		val dk = unwrap(oldPassphrase) ?: return false
		try {
			write(dk, newPassphrase)
			return true
		} finally {
			Sodium.memzero(dk)
		}
	}

	fun currentKdfId(): Int {
		val raw = AtomicFile.readFirstValid(locations(), Format.KEYFILE_LEN) ?: return -1
		return raw[6].toInt()
	}

	/** Returns the NEW data key. Caller must re-encrypt every blob under it
	 *  BEFORE this returns — see DataStore.rotateDataKey. */
	fun rotate(newPassphrase: CharArray?): ByteArray {
		val dk = ByteArray(Sodium.KEY_BYTES).also { Sodium.randomBytes(it) }
		write(dk, newPassphrase)
		return dk
	}

	private fun deriveKek(
		passphrase: CharArray, salt: ByteArray,
		mKib: Long = Format.ARGON_M_KIB, t: Long = Format.ARGON_T
	): ByteArray = Passphrase.withBytes(passphrase) { pw ->
		val kek = ByteArray(Sodium.KEY_BYTES)
		val rc = Sodium.pwhash(kek, pw, salt, t, mKib)
		if (rc != 0) {
			Sodium.memzero(kek)
			throw LowMemoryException(
				"Not enough free memory to unlock. The encryption step needs about " +
						"${mKib / 1024} MB — close some apps and try again."
			)
		}
		kek
	}
}