package com.liblens.xyznotes

import android.content.Context
import com.liblens.xyznotes.crypto.AtomicFile
import com.liblens.xyznotes.crypto.Blob
import com.liblens.xyznotes.crypto.CryptoException
import java.io.File

/** Per-blob encrypted storage. One file on disk per logical path.
 *  Blast radius of an interrupted write is one blob, never the corpus.
 *
 *  Paths are VAULT-RELATIVE. The vault is selected by [activeVault], not by
 *  the path, and never appears in a logical path or in AAD. That separation is
 *  what lets a note move between vaults by resealing under the destination's
 *  key, and lets a vault be renamed without touching a single blob. */
object BlobStore {

	/** Storage detail only. The extension is never part of a logical path, an
	 *  AAD, or a tar entry name — those all use the bare path. Anything that
	 *  appends it to an identity is a bug. */
	private const val EXT = ".xyn"

	private var appContext: Context? = null

	@Volatile
	private var activeVaultId: String = VaultStore.DEFAULT_VAULT_ID

	/** Called once from XyzNotesApp.onCreate, before any Activity exists. */
	fun init(context: Context) {
		if (appContext == null) appContext = context.applicationContext
	}

	/** /sdcard/Android/data/<appId>/files/<DATA_DIR>  — no permission required.
	 *  Removed on uninstall; that is why exports go through SAF to a
	 *  user-chosen location the app does not control. */
	fun dataRoot(): File {
		val ctx = appContext ?: error("BlobStore.init() must be called before storage access")
		return File(ctx.getExternalFilesDir(null), BuildConfig.DATA_DIR).also { it.mkdirs() }
	}

	// ── Vault selection ───────────────────────────────────────────────────

	fun activeVault(): String = activeVaultId

	/** Switching vaults while one is unlocked would let blobs be read with the
	 *  wrong key — every read would fail authentication, but the failure would
	 *  look like corruption. DataStore must lock before calling this. */
	fun setActiveVault(vaultId: String) {
		check(!DataStore.isUnlocked()) { "Lock the current vault before switching" }
		activeVaultId = vaultId
	}

	/** The active vault's directory. */
	fun root(): File = File(VaultStore.vaultDir(activeVaultId), "blobs").also { it.mkdirs() }

	/** Vault-scoped, for callers that must address a specific vault rather than
	 *  the active one — export, import, and merge. */
	fun rootOf(vaultId: String): File =
		File(VaultStore.vaultDir(vaultId), "blobs").also { it.mkdirs() }

	// ── Blob access ───────────────────────────────────────────────────────

	private fun fileFor(path: String) = File(root(), path + EXT)

	fun exists(path: String) = fileFor(path).exists()

	fun read(path: String, dataKey: ByteArray?): ByteArray? {
		val f = fileFor(path)
		if (!f.exists()) return null
		return Blob.open(f.readBytes(), path, dataKey)
	}

	fun readText(path: String, dataKey: ByteArray?): String =
		read(path, dataKey)?.toString(Charsets.UTF_8) ?: ""

	fun write(path: String, content: ByteArray, dataKey: ByteArray?, encrypted: Boolean) {
		AtomicFile.write(fileFor(path), Blob.seal(content, path, dataKey, encrypted))
	}

	fun writeText(path: String, content: String, dataKey: ByteArray?, encrypted: Boolean) =
		write(path, content.toByteArray(Charsets.UTF_8), dataKey, encrypted)

	fun delete(path: String) { fileFor(path).delete() }

	fun move(from: String, to: String, dataKey: ByteArray?, encrypted: Boolean) {
		// AAD is bound to the path, so a rename must re-seal. A bare file rename
		// would produce a blob that fails authentication at its new location.
		val content = read(from, dataKey) ?: return
		write(to, content, dataKey, encrypted)
		delete(from)
	}

	/** All logical paths under a prefix, in the active vault. */
	fun list(prefix: String = ""): List<String> {
		val base = root()
		if (!base.exists()) return emptyList()
		return base.walkTopDown()
			.filter { it.isFile && it.name.endsWith(EXT) }
			.map { it.relativeTo(base).path.replace(File.separatorChar, '/').removeSuffix(EXT) }
			.filter { it.startsWith(prefix) }
			.toList()
	}

	fun deletePrefix(prefix: String) {
		list(prefix).forEach { delete(it) }
		File(root(), prefix).takeIf { it.isDirectory }?.deleteRecursively()
	}

	// ── Staged reseal (see Transition) ────────────────────────────────────

	/** Writes staged replacements for every blob, under a new key.
	 *  Nothing is committed until commitStaged(). */
	fun stageReseal(paths: List<String>, oldKey: ByteArray?, newKey: ByteArray?, encrypted: Boolean) {
		paths.forEach { path ->
			val plain = read(path, oldKey) ?: return@forEach
			val staged = File(root(), "$path$EXT.staged")
			staged.parentFile?.mkdirs()
			AtomicFile.write(staged, Blob.seal(plain, path, newKey, encrypted))
		}
	}

	fun commitStaged() {
		root().walkTopDown()
			.filter { it.isFile && it.name.endsWith("$EXT.staged") }
			.toList()
			.forEach { staged ->
				val target = File(staged.parentFile, staged.name.removeSuffix(".staged"))
				if (!staged.renameTo(target))
					throw CryptoException("Failed to commit ${target.name}")
			}
	}

	/** Scoped to the active vault's blobs directory, and to the two suffixes
	 *  this class creates. A recursive delete over dataRoot() would be one
	 *  naming collision away from destroying a real blob. */
	fun discardStaged() {
		root().walkTopDown()
			.filter { it.isFile && (it.name.endsWith("$EXT.staged") || it.name.endsWith("$EXT.tmp")) }
			.toList()
			.forEach { it.delete() }
	}
}