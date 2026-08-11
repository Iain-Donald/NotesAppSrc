package com.liblens.xyznotes

import java.io.OutputStream

/**
 * Minimal POSIX (ustar) tar writer. Write-only by design — reading arbitrary
 * tar is a much larger problem, and we only need it if import is added later.
 *
 * Long paths use PAX extended headers (typeflag 'x'), because our logical
 * paths routinely exceed tar's 100-byte name field:
 *   userData/sections/<id>-<64>/<id>-<64>.txt  ≈ 180 bytes worst case.
 */
class TarWriter(private val out: OutputStream) : AutoCloseable {

	private companion object {
		const val BLOCK = 512
		const val NAME_MAX = 100
	}

	fun addFile(path: String, content: ByteArray, modTimeSecs: Long = 0L) {
		val nameBytes = path.toByteArray(Charsets.UTF_8)
		if (nameBytes.size > NAME_MAX) writePaxHeader(path, modTimeSecs)
		writeHeader(
			name = if (nameBytes.size > NAME_MAX) truncate(path) else path,
			size = content.size.toLong(),
			typeFlag = '0'.code.toByte(),
			modTimeSecs = modTimeSecs
		)
		out.write(content)
		pad(content.size.toLong())
	}

	/** PAX record: "<totalLen> path=<value>\n", where totalLen counts itself. */
	private fun writePaxHeader(path: String, modTimeSecs: Long) {
		val value = "path=$path\n"
		var len = value.toByteArray(Charsets.UTF_8).size + 3   // seed: " " + 1-2 digits
		repeat(3) { len = value.toByteArray(Charsets.UTF_8).size + len.toString().length + 1 }
		val record = "$len $value".toByteArray(Charsets.UTF_8)

		writeHeader("PaxHeader", record.size.toLong(), 'x'.code.toByte(), modTimeSecs)
		out.write(record)
		pad(record.size.toLong())
	}

	private fun writeHeader(name: String, size: Long, typeFlag: Byte, modTimeSecs: Long) {
		val h = ByteArray(BLOCK)
		putString(h, 0, 100, name)
		putOctal(h, 100, 8, 0b110_100_100L)      // mode 0644
		putOctal(h, 108, 8, 0L)                  // uid
		putOctal(h, 116, 8, 0L)                  // gid
		putOctal(h, 124, 12, size)
		putOctal(h, 136, 12, modTimeSecs)
		h[156] = typeFlag
		putString(h, 257, 6, "ustar")
		h[263] = '0'.code.toByte(); h[264] = '0'.code.toByte()   // version "00"

		// Checksum is computed with the checksum field itself read as spaces.
		for (i in 148 until 156) h[i] = ' '.code.toByte()
		var sum = 0L
		for (b in h) sum += (b.toInt() and 0xFF).toLong()
		putOctal(h, 148, 7, sum)
		h[155] = ' '.code.toByte()

		out.write(h)
	}

	private fun truncate(path: String) = String(
		path.toByteArray(Charsets.UTF_8).copyOf(NAME_MAX), Charsets.UTF_8
	).trimEnd('\uFFFD')

	private fun putString(b: ByteArray, off: Int, len: Int, s: String) {
		val v = s.toByteArray(Charsets.UTF_8)
		v.copyInto(b, off, 0, minOf(v.size, len - 1))
	}

	/** Octal, right-aligned, zero-padded, NUL-terminated. */
	private fun putOctal(b: ByteArray, off: Int, len: Int, value: Long) {
		val s = value.toString(8).padStart(len - 1, '0')
		s.toByteArray(Charsets.US_ASCII).copyInto(b, off, 0, len - 1)
		b[off + len - 1] = 0
	}

	private fun pad(size: Long) {
		val rem = (size % BLOCK).toInt()
		if (rem != 0) out.write(ByteArray(BLOCK - rem))
	}

	/** Two zero blocks mark end-of-archive. */
	override fun close() {
		out.write(ByteArray(BLOCK * 2))
		out.flush()
	}
}