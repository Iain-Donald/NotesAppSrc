package com.liblens.xyznotes

import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun apply() {
        val theme = PreferencesManager.load().theme
        android.util.Log.i("XYNC", "apply() theme=$theme from ${Thread.currentThread().stackTrace[3]}")
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