package de.bgghome.webtrees.nativ.data

import android.content.Context

/** Kleine, unkritische Einstellungen. Das Passwort wird nie gespeichert - nur der Sitzungs-Cookie. */
class Settings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("baseUrl", "").orEmpty()
        set(value) = prefs.edit().putString("baseUrl", value).apply()

    var userName: String
        get() = prefs.getString("userName", "").orEmpty()
        set(value) = prefs.edit().putString("userName", value).apply()

    var tree: String
        get() = prefs.getString("tree", "").orEmpty()
        set(value) = prefs.edit().putString("tree", value).apply()
}
