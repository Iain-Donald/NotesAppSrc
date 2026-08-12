package com.liblens.xyznotes

object ThemeManager {

    /** Loads the stored preference into Palette. Deliberately does NOT touch
     *  AppCompatDelegate: no resource-qualifier switching, no Activity
     *  recreation, no config churn. Colors are applied per-view instead. */
    fun apply() {
        Palette.setDark(PreferencesManager.load().theme != "light")
    }

    fun switch(theme: String) {
        PreferencesManager.save(PreferencesManager.load().copy(theme = theme))
        Palette.setDark(theme != "light")
    }
}