package com.liblens.xyznotes.crypto

internal object Format {
	val MAGIC = byteArrayOf('X'.code.toByte(), 'Y'.code.toByte(),
		'N'.code.toByte(), 'C'.code.toByte())
	const val VERSION = 2

	const val KDF_NONE = 0
	const val KDF_ARGON2ID = 2

	const val AEAD_NONE = 0
	const val AEAD_XCHACHA = 2

	// Argon2id parameters. Recorded per-container so these can change later.
	const val ARGON_M_KIB = 65536L   // 64 MiB
	const val ARGON_T = 3L
	const val ARGON_P = 1L           // recorded only; crypto_pwhash is p=1 internally

	const val KEYFILE_LEN = 108
	const val BLOB_HEADER_LEN = 32

	fun putU16(b: ByteArray, off: Int, v: Int) {
		b[off] = (v ushr 8).toByte(); b[off + 1] = v.toByte()
	}
	fun getU16(b: ByteArray, off: Int): Int =
		((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

	fun putU32(b: ByteArray, off: Int, v: Long) {
		b[off] = (v ushr 24).toByte(); b[off + 1] = (v ushr 16).toByte()
		b[off + 2] = (v ushr 8).toByte(); b[off + 3] = v.toByte()
	}
	fun getU32(b: ByteArray, off: Int): Long =
		((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
				((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

	fun hasMagic(b: ByteArray): Boolean =
		b.size >= 6 && b[0] == MAGIC[0] && b[1] == MAGIC[1] &&
				b[2] == MAGIC[2] && b[3] == MAGIC[3]
}

class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LowMemoryException(message: String) : Exception(message)