package io.pocketshare

import android.content.Context

object SpeedSettings {
    const val KEY = "speed_interval_ms"
    val intervals = longArrayOf(1000, 2000, 3000, 5000, 10000)
    fun interval(context: Context): Long = context.getSharedPreferences("settings", 0)
        .getLong(KEY, 1000L).takeIf { it in intervals } ?: 1000L
    fun caption(context: Context) = context.getString(R.string.speed_interval_caption, interval(context) / 1000)
}
