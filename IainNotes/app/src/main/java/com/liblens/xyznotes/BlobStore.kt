package com.liblens.xyznotes

import android.content.Context
import android.os.Environment
import com.liblens.xyznotes.crypto.AtomicFile
import com.liblens.xyznotes.crypto.Blob
import com.liblens.xyznotes.crypto.CryptoException
import java.io.File

/** Per-blob encrypted storage. One file on disk per logical path.
 *  Blast radius of an interrupted write is one blob, never the corpus. */
object BlobStore {

	private const val EXT = ".xyn"
	private var appContext: Context? = null
	/** Called once from XyzNotesApp.onCreate, before any Activity exists. */
	fun init(context: Context) {
		if (appContext == null) appContext = context.applicationContext
	}

	/** /sdcard/Android/data/<appId>/files/  — no permission required.
	 *  Removed on uninstall; that is why exports go through SAF to a
	 *  user-chosen location the app does not control. */
	fun root(): File {
		val ctx = appContext ?: error("BlobStore.init() must be called before storage access")
		return File(ctx.getExternalFilesDir(null), BuildConfig.DATA_DIR).also { it.mkdirs() }
	}

	/** Logical path -> on-disk file. Filename discipline is load-bearing:
	 *  ids stay recoverable from filenames for recoverFromDisk(). */
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

	/** All logical paths under a prefix. */
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

	/** Writes staged .tmp replacements for every blob, under a new key.
	 *  Nothing is committed until commitStaged(). See Transition. */
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

	fun discardStaged() {
		root().walkTopDown()
			.filter { it.isFile && (it.name.endsWith(".staged") || it.name.endsWith(".tmp")) }
			.toList()
			.forEach { it.delete() }
	}
}