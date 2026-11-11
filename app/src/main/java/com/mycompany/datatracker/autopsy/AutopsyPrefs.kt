package com.crestpoint.datatracker.autopsy

import android.content.Context

class AutopsyPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("autopsy", Context.MODE_PRIVATE)

    var spikeMb: Int
        get() = sp.getInt("spike_mb", 15)
        set(v) = sp.edit().putInt("spike_mb", v.coerceAtLeast(1)).apply()

    var windowMin: Int
        get() = sp.getInt("window_min", 5)
        set(v) = sp.edit().putInt("window_min", v.coerceAtLeast(1)).apply()

    var cooldownMin: Int
        get() = sp.getInt("cooldown_min", 10)
        set(v) = sp.edit().putInt("cooldown_min", v.coerceAtLeast(1)).apply()

    companion object {
        const val TICK_MS = 60_000L
    }
}
