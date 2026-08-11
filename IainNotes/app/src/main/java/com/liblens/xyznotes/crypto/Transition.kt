package com.liblens.xyznotes.crypto

import android.os.Environment
import com.liblens.xyznotes.BlobStore
import java.io.File
import com.liblens.xyznotes.BuildConfig

/** Marks an in-flight passphrase enable/disable so an interrupted run is recoverable. */
internal object Transition {

	private fun marker() = File(BlobStore.root(), ".transition")

	fun begin(targetEncrypted: Boolean) {
		AtomicFile.write(marker(), (if (targetEncrypted) "encrypt" else "decrypt").toByteArray())
	}

	fun clear() { marker().delete() }

	fun isPending(): Boolean = marker().exists()

	/** Called at startup. Staged blobs are complete and fsynced by the time the
	 *  key file is written, so resuming means committing them. If the key file
	 *  was never written, discarding is correct. Distinguish by key-file kdf id. */
	fun recover(expectedEncrypted: Boolean) {
		if (!isPending()) return
		val keyFileMatches = (KeyFile.currentKdfId() == Format.KDF_ARGON2ID) == expectedEncrypted
		if (keyFileMatches) BlobStore.commitStaged() else BlobStore.discardStaged()
		clear()
	}
}