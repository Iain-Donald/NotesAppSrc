package com.liblens.xyznotes

import com.liblens.xyznotes.crypto.AtomicFile
import com.liblens.xyznotes.crypto.Format
import com.liblens.xyznotes.crypto.KeyFile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class UserPreferences(
    val usePassphrase: Boolean = false,
    val lockOnClose: Boolean = false,
    val theme: String = "dark",
    val dndPromptDismissed: Boolean = false,
    val notifPromptShown: Boolean = false
)

object PreferencesManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Read once per process. ThemeManager.apply() runs in every Activity's
     *  onCreate, so an uncached load() is a file read and a JSON parse on the
     *  critical path before first paint, seven times over. */
    @Volatile private var cached: UserPreferences? = null

    private fun prefsFile() = File(BlobStore.root(), "user/preferences.json")
        .also { it.parentFile?.mkdirs() }

    /** Never throws, and never deletes or rewrites a damaged file.
     *
     *  Preferences describe presentation, not data. A corrupt prefs file must
     *  not block startup: an app that will not open drives the user to Clear
     *  Storage, which on this storage layout destroys every note. That is a
     *  catastrophically disproportionate outcome for a damaged settings file,
     *  so this path degrades instead of failing.
     *
     *  usePassphrase is deliberately NOT trusted from this file — KeyFile is
     *  the ground truth for whether the container is encrypted, so a damaged
     *  or absent prefs file cannot cause the app to skip the passphrase
     *  prompt or prompt when there is no passphrase. */
    fun load(): UserPreferences {
        cached?.let { return it }

        val f = prefsFile()
        val stored: UserPreferences? = if (!f.exists()) {
            null   // first run, not corruption
        } else {
            try {
                json.decodeFromString<UserPreferences>(f.readText())
            } catch (e: SerializationException) {
                quarantine(f)
                Fail.warn("preferences.json corrupt; using defaults", e)
                null
            } catch (e: IOException) {
                // Transient read failure. Do NOT quarantine — the file may be
                // perfectly good and unreadable for an unrelated reason.
                Fail.warn("preferences.json unreadable; using defaults", e)
                null
            }
        }

        val prefs = (stored ?: UserPreferences())
            .copy(usePassphrase = KeyFile.currentKdfId() == Format.KDF_ARGON2ID)

        // Only cache a clean read. A degraded result must not be frozen in for
        // the life of the process, and must not be written back over the file.
        if (stored != null) cached = prefs
        return prefs
    }

    /** Moves a corrupt file aside rather than overwriting it. Costs a few
     *  hundred bytes and keeps the original recoverable. */
    private fun quarantine(f: File) {
        try {
            f.renameTo(File(f.parentFile, "${f.name}.corrupt.${System.currentTimeMillis()}"))
        } catch (e: SecurityException) {
            Fail.warn("could not quarantine preferences.json", e)
        }
    }

    fun save(prefs: UserPreferences) {
        AtomicFile.write(
            prefsFile(),
            json.encodeToString(prefs).toByteArray(Charsets.UTF_8)
        )
        cached = prefs
    }
}