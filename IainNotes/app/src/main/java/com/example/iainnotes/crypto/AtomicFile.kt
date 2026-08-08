package com.example.iainnotes.crypto

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object AtomicFile {

	/** tmp -> fsync -> atomic rename -> fsync parent. Survives process kill at any point:
	 *  either the old content or the new content is present, never a mix. */
	fun write(target: File, bytes: ByteArray) {
		target.parentFile?.mkdirs()
		val tmp = File(target.parentFile, "${target.name}.tmp")
		try {
			FileOutputStream(tmp).use { fos ->
				fos.write(bytes)
				fos.flush()
				fos.fd.sync()
			}
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
			syncDir(target.parentFile)
		} catch (e: Exception) {
			tmp.delete()
			throw e
		}
	}

	/** Renames are only durable once the directory entry itself is synced. */
	private fun syncDir(dir: File?) {
		if (dir == null) return
		try {
			RandomAccessFile(dir, "r").use { it.fd.sync() }
		} catch (_: Exception) {
			// Some filesystems reject directory fsync. Non-fatal.
		}
	}

	/** Writes the same bytes to several paths. Throws only if ALL fail. */
	fun writeRedundant(targets: List<File>, bytes: ByteArray) {
		var lastError: Exception? = null
		var wrote = 0
		val detail = StringBuilder()
		for (t in targets) {
			try {
				write(t, bytes); wrote++
			} catch (e: Exception) {
				lastError = e
				detail.append("\n${t.absolutePath}: ${e.javaClass.simpleName}: ${e.message}")
			}
		}
		if (wrote == 0) throw CryptoException(
			"Could not write key file to any location:$detail", lastError
		)
	}

	/** Returns the first target that reads back with a valid header. */
	fun readFirstValid(targets: List<File>, minLen: Int): ByteArray? {
		for (t in targets) {
			try {
				if (!t.exists()) continue
				val b = t.readBytes()
				if (b.size >= minLen && Format.hasMagic(b)) return b
			} catch (_: Exception) { }
		}
		return null
	}
}