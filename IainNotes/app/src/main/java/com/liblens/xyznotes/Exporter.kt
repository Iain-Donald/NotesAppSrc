package com.liblens.xyznotes

import com.liblens.xyznotes.crypto.CryptoException
import com.liblens.xyznotes.crypto.KeyFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object Exporter {

	/** Writes the archive to an arbitrary stream, so the caller can hand us a
	 *  SAF-provided OutputStream for a location the user chose. */
	suspend fun exportTo(
		stream: OutputStream,
		dataKey: ByteArray?,
		storedEncrypted: Boolean,
		encryptedExport: Boolean
	): Unit = withContext(Dispatchers.IO) {
		require(!encryptedExport || storedEncrypted) {
			"Cannot export encrypted — no passphrase is set"
		}
		TarWriter(stream.buffered()).use { tar ->
			BlobStore.list().forEach { path ->
				if (encryptedExport) {
					tar.addFile("$path.xyn", File(BlobStore.root(), "$path.xyn").readBytes())
				} else {
					tar.addFile(path, BlobStore.read(path, dataKey)
						?: throw CryptoException("Could not read $path for export"))
				}
			}
			AlarmStore.file().takeIf { it.exists() }
				?.let { tar.addFile("userData/alarms.json", it.readBytes()) }
			if (encryptedExport) {
				KeyFile.locations().first().takeIf { it.exists() }
					?.let { tar.addFile("keys.xync", it.readBytes()) }
			}
		}
	}

	fun suggestedName(encryptedExport: Boolean): String {
		val stamp = currentTimestamp()
		return if (encryptedExport) "XyzNotes-$stamp.enc.tar" else "XyzNotes-$stamp.tar"
	}
}