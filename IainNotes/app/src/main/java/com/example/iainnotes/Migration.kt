package com.example.iainnotes

import android.os.Environment
import com.example.iainnotes.crypto.*
import java.io.File

object Migration {

	private fun legacyEnc() = File(Environment.getExternalStorageDirectory(), "${BuildConfig.DATA_DIR}/IainNotes.tar.enc")
	private fun legacyPlain() = File(Environment.getExternalStorageDirectory(), "${BuildConfig.DATA_DIR}/IainNotes.tar")

	fun hasLegacyContainer() = legacyEnc().exists() || legacyPlain().exists()

	/** Returns the new data key, or null if the passphrase was wrong. */
	fun migrateV1ToV2(passphrase: CharArray?): ByteArray? {
		val encFile = legacyEnc()
		val useEnc = encFile.exists()
		val src = if (useEnc) encFile else legacyPlain()
		if (!src.exists()) return null

		val tarBytes = if (useEnc) {
			if (passphrase == null || passphrase.isEmpty()) return null
			try { CryptoLegacy.decrypt(src.readBytes(), passphrase) } catch (_: Exception) { return null }
		} else src.readBytes()

		val files = TarManager.unpack(tarBytes)
		val willEncrypt = useEnc
		val dk = ByteArray(Sodium.KEY_BYTES).also { Sodium.randomBytes(it) }

		// Blobs first, key file last — same invariant as retransition().
		files.forEach { (path, content) ->
			val logical = path.removeSuffix("/")
			val encryptThis = willEncrypt && logical != "userData/map.json"
			BlobStore.write(normalise(logical), content, dk, encryptThis)
		}
		KeyFile.write(dk, if (willEncrypt) passphrase else null)

		// Retained deliberately. Never deleted by the app.
		src.renameTo(File(src.parentFile, "${src.name}.v1bak"))
		return dk
	}

	/** v1 stored map at userData/map.json; v2's logical path drops the extension. */
	private fun normalise(path: String) =
		if (path == "userData/map.json") "userData/map" else path
}