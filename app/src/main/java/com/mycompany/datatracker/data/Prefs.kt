package com.mycompany.datatracker.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    var alertsEnabled: Boolean
        get() = sp.getBoolean("alerts", true)
        set(v) = sp.edit().putBoolean("alerts", v).apply()

    /** Daily total cap per app in MB. 0 = off. */
    var appCapMb: Int
        get() = sp.getInt("app_cap_mb", 500)
        set(v) = sp.edit().putInt("app_cap_mb", v).apply()

    /** Daily background-data threshold per app in MB. 0 = off. */
    var bgCapMb: Int
        get() = sp.getInt("bg_cap_mb", 100)
        set(v) = sp.edit().putInt("bg_cap_mb", v).apply()

    var lastBackfillDay: String
        get() = sp.getString("last_backfill", "") ?: ""
        set(v) = sp.edit().putString("last_backfill", v).apply()

    var debugUsagePercent: Int
        get() = sp.getInt("debug_usage_percent", 0)
        set(v) = sp.edit().putInt("debug_usage_percent", v.coerceIn(0, 100)).apply()

    var notificationsRequested: Boolean
        get() = sp.getBoolean("notifications_requested", false)
        set(v) = sp.edit().putBoolean("notifications_requested", v).apply()

    fun wasNotified(key: String) = sp.getBoolean("n_$key", false)

    fun markNotified(key: String) = sp.edit().putBoolean("n_$key", true).apply()

    /** Forget alert flags from previous days. */
    fun pruneNotified(todayKey: String) {
        val editor = sp.edit()
        for (k in sp.all.keys) {
            if (k.startsWith("n_") && !k.endsWith("_$todayKey")) editor.remove(k)
        }
        editor.apply()
    }
    /** Daily data limit in MB to trigger the report card warning. */
    var dailyLimitMb: Int
        get() = sp.getInt("daily_limit_mb", 500)
        set(v) = sp.edit().putInt("daily_limit_mb", v.coerceAtLeast(1)).apply()
}
