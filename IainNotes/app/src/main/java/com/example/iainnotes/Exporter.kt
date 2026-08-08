package com.example.iainnotes

import android.os.Environment
import com.example.iainnotes.crypto.KeyFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {

	/** encryptedExport=true  -> sealed blobs + key file; needs the passphrase to open.
	 *  encryptedExport=false -> plaintext files, readable in any zip tool. */
	suspend fun export(
		dataKey: ByteArray?, storedEncrypted: Boolean, encryptedExport: Boolean
	): File = withContext(Dispatchers.IO) {
		val name = if (encryptedExport) "IainNotes-export.enc.zip" else "IainNotes-export.zip"
		val out = File(BlobStore.root(), name)

		ZipOutputStream(FileOutputStream(out).buffered()).use { zip ->
			BlobStore.list().forEach { path ->
				if (encryptedExport) {
					// Raw sealed bytes, byte-identical to what's on disk.
					val f = File(BlobStore.root(), "$path.xyn")
					zip.putNextEntry(ZipEntry("$path.xyn")); zip.write(f.readBytes()); zip.closeEntry()
				} else {
					val plain = BlobStore.read(path, dataKey) ?: return@forEach
					zip.putNextEntry(ZipEntry(path)); zip.write(plain); zip.closeEntry()
				}
			}
			AlarmStore.file().takeIf { it.exists() }?.let {
				zip.putNextEntry(ZipEntry("userData/alarms.json")); zip.write(it.readBytes()); zip.closeEntry()
			}
			if (encryptedExport) {
				// Self-contained: an export can be restored even if the install is gone.
				KeyFile.locations().first().takeIf { it.exists() }?.let {
					zip.putNextEntry(ZipEntry("keys.xync")); zip.write(it.readBytes()); zip.closeEntry()
				}
			}
		}
		out
	}
}