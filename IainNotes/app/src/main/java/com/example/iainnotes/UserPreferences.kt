package com.example.iainnotes

import android.os.Environment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class UserPreferences(
    val usePassphrase: Boolean = false,
    val lockOnClose: Boolean = false,
    val theme: String = "dark"   // "light", "dark", "amoled"
)

object PreferencesManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private fun prefsFile() = File(
        Environment.getExternalStorageDirectory(),
        "IainNotes/user/preferences.json"
    ).also { it.parentFile?.mkdirs() }

    fun load(): UserPreferences {
        val f = prefsFile()
        if (!f.exists()) return UserPreferences()
        return try {
            json.decodeFromString(f.readText())
        } catch (e: Exception) {
            UserPreferences()
        }
    }

    fun save(prefs: UserPreferences) {
        prefsFile().writeText(json.encodeToString(prefs))
    }
}