package com.akbigchris.copyjob

import java.util.prefs.Preferences

/**
 * Small persisted settings for the app, backed by [Preferences] (the Windows registry
 * under HKCU\Software\JavaSoft\Prefs\... on Windows). No extra config file to manage.
 */
object AppPreferences {
    private val preferences: Preferences = Preferences.userNodeForPackage(AppPreferences::class.java)
    private const val KEY_LAST_JSON_PATH = "lastJsonPath"

    var lastJsonPath: String?
        get() = preferences.get(KEY_LAST_JSON_PATH, null)
        set(value) {
            if (value.isNullOrBlank()) {
                preferences.remove(KEY_LAST_JSON_PATH)
            } else {
                preferences.put(KEY_LAST_JSON_PATH, value)
            }
        }
}
