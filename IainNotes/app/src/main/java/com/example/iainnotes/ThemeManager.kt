package com.example.iainnotes

import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun apply() {
        val theme = PreferencesManager.load().theme
        val mode = when (theme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark", "amoled" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun switch(theme: String) {
        val prefs = PreferencesManager.load()
        PreferencesManager.save(prefs.copy(theme = theme))
        apply()
    }
}