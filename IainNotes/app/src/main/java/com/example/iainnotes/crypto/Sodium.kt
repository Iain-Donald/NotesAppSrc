package com.example.iainnotes.crypto

internal object Sodium {
	init {
		System.loadLibrary("xyncrypto")
		check(init() >= 0) { "libsodium failed to initialise" }
	}

	external fun init(): Int
	external fun randomBytes(out: ByteArray)
	external fun pwhash(
		out: ByteArray, passwd: ByteArray, salt: ByteArray,
		opslimit: Long, memlimitKib: Long
	): Int
	external fun aeadEncrypt(msg: ByteArray, ad: ByteArray?, nonce: ByteArray, key: ByteArray): ByteArray?
	external fun aeadDecrypt(ct: ByteArray, ad: ByteArray?, nonce: ByteArray, key: ByteArray): ByteArray?
	external fun memzero(buf: ByteArray)






	// ABI-stable constants, asserted rather than queried.
	const val SALT_BYTES = 16
	const val NONCE_BYTES = 24
	const val KEY_BYTES = 32
	const val TAG_BYTES = 16
}