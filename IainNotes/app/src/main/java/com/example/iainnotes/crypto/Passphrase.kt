package com.example.iainnotes.crypto

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal object Passphrase {

	/** UTF-8 encodes without ever materialising a String.
	 *  Caller owns the result and must zero it. */
	fun toBytes(passphrase: CharArray): ByteArray {
		val charBuf = CharBuffer.wrap(passphrase)
		val encoder = StandardCharsets.UTF_8.newEncoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE)
		val byteBuf = encoder.encode(charBuf)

		val out = ByteArray(byteBuf.remaining())
		byteBuf.get(out)

		// encode() allocates its own backing array; scrub it before release.
		if (byteBuf.hasArray()) byteBuf.array().fill(0)
		return out
	}

	inline fun <T> withBytes(passphrase: CharArray, block: (ByteArray) -> T): T {
		val b = toBytes(passphrase)
		try {
			return block(b)
		} finally {
			Sodium.memzero(b)
		}
	}
}