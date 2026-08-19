package com.liblens.xyznotes

import com.liblens.xyznotes.crypto.AtomicFile
import com.liblens.xyznotes.crypto.Format
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** Identity and metadata for a single vault.
 *
 *  Plaintext, deliberately. It has to be readable before the vault is unlocked
 *  — the picker needs to list vaults, and an alarm needs to know which vault it
 *  belongs to — so nothing sensitive goes in here. `name` IS user-visible
 *  content and IS exposed at rest; that is the same trade already made for note
 *  titles in map.json, and it must be disclosed in the same place. */
@Serializable
data class VaultManifest(
    val id: String,
    val name: String,
    val formatVersion: Int = Format.VERSION,
    val createdUtc: Long = 0L,
    /** Cached from the key file for display. KeyFile is the ground truth;
     *  never trust this for a security decision. */
    val encrypted: Boolean = false
)

@Serializable
data class VaultIndex(
    val vaults: List<String> = emptyList(),
    val lastOpened: String? = null
)

/** The vault registry. Owns the on-disk layout:
 *
 *      <DATA_DIR>/
 *        vaults.json                    index — ordering, last opened
 *        vaults/<vaultId>/
 *          vault.json                   this vault's manifest
 *          keys.xync  keys.xync.bak     wrapped data key, redundant copies
 *          userData/.keys.xync          third copy
 *          blobs/<logical path>.xyn     one file per note or index
 *
 *  Every vault is a complete, self-contained container: its own key, its own
 *  map, its own alarms. Nothing is shared between them but this index. That is
 *  what makes export and import tractable later — an archive is one vault's
 *  directory, and importing is creating a vault from one. */
object VaultStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    const val DEFAULT_VAULT_ID = "default"

    private fun indexFile() = File(BlobStore.dataRoot(), "vaults.json")

    fun vaultDir(vaultId: String): File =
        File(BlobStore.dataRoot(), "vaults/$vaultId").also { it.mkdirs() }

    fun manifestFile(vaultId: String) = File(vaultDir(vaultId), "vault.json")

    fun exists(vaultId: String) = manifestFile(vaultId).exists()

    // ── Index ─────────────────────────────────────────────────────────────

    /** Never throws. A damaged index costs ordering, not data — the vault
     *  directories are the ground truth and can be rescanned. Blocking startup
     *  over it would push the user toward Clear Storage, which destroys
     *  everything. */
    fun loadIndex(): VaultIndex {
        val f = indexFile()
        if (!f.exists()) return VaultIndex()
        return try {
            json.decodeFromString<VaultIndex>(f.readText())
        } catch (e: SerializationException) {
            Fail.warn("vaults.json corrupt; rebuilding from disk", e)
            rebuildIndex()
        } catch (e: IOException) {
            Fail.warn("vaults.json unreadable; rebuilding from disk", e)
            rebuildIndex()
        }
    }

    fun saveIndex(index: VaultIndex) {
        AtomicFile.write(indexFile(), json.encodeToString(index).toByteArray(Charsets.UTF_8))
    }

    /** Recovery path: the directory listing is authoritative, the index is a
     *  convenience. A vault whose directory exists is never lost to a bad
     *  index. */
    fun rebuildIndex(): VaultIndex {
        val base = File(BlobStore.dataRoot(), "vaults")
        if (!base.isDirectory) return VaultIndex()
        val ids = base.listFiles()
            ?.filter { it.isDirectory && File(it, "vault.json").exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        return VaultIndex(vaults = ids)
    }

    // ── Manifests ─────────────────────────────────────────────────────────

    fun loadManifest(vaultId: String): VaultManifest? {
        val f = manifestFile(vaultId)
        if (!f.exists()) return null
        return try {
            json.decodeFromString<VaultManifest>(f.readText())
        } catch (e: SerializationException) {
            // Salvageable: the id is the directory name and the rest is
            // cosmetic. Losing the manifest must not lose the vault.
            Fail.warn("vault.json corrupt for $vaultId; synthesising", e)
            VaultManifest(id = vaultId, name = vaultId)
        } catch (e: IOException) {
            Fail.warn("vault.json unreadable for $vaultId", e)
            null
        }
    }

    fun saveManifest(m: VaultManifest) {
        AtomicFile.write(
            manifestFile(m.id),
            json.encodeToString(m).toByteArray(Charsets.UTF_8)
        )
    }

    fun listVaults(): List<VaultManifest> {
        val index = loadIndex()
        val ids = if (index.vaults.isEmpty()) rebuildIndex().vaults else index.vaults
        return ids.mapNotNull { loadManifest(it) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    /** Creates the directory and manifest only. The caller must then create
     *  the key file and an empty map through DataStore — this deliberately
     *  does not, so that a half-created vault is an empty directory rather
     *  than one with a key and no content. */
    fun createVault(name: String, id: String = newVaultId()): VaultManifest {
        check(!exists(id)) { "Vault $id already exists" }
        val m = VaultManifest(
            id = id,
            name = name,
            createdUtc = System.currentTimeMillis()
        )
        saveManifest(m)
        val index = loadIndex()
        saveIndex(index.copy(vaults = index.vaults + id))
        return m
    }

    /** Random, not sequential. A vault id has to survive being carried to
     *  another device and merged, so it must not collide with an id another
     *  device minted independently — which a timestamp or counter eventually
     *  will. 128 bits makes that impossible in practice. */
    fun newVaultId(): String {
        val b = ByteArray(16)
        java.security.SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Irreversible: removes the key file along with the blobs, so the
     *  contents become undecryptable even if the files are later recovered.
     *  Caller MUST have confirmed with the user. */
    fun deleteVault(vaultId: String) {
        vaultDir(vaultId).deleteRecursively()
        val index = loadIndex()
        saveIndex(
            index.copy(
                vaults = index.vaults - vaultId,
                lastOpened = index.lastOpened?.takeIf { it != vaultId }
            )
        )
    }
}