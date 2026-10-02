package io.pocketshare

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bounded service-event history, shared by activity and service. */
object EventLog {
    @Synchronized fun append(context: Context, message: String) {
        if (message.isBlank()) return
        val prefs = context.getSharedPreferences("events", Context.MODE_PRIVATE)
        var history = prefs.getString("history", "") + SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) + "  $message\n\n"
        if (history.length > 24000) {
            val cut = history.indexOf("\n\n", history.length - 20000)
            history = history.substring(if (cut < 0) history.length - 20000 else cut + 2)
        }
        prefs.edit().putString("history", history).apply()
    }
    @Synchronized fun read(context: Context) = context.getSharedPreferences("events", Context.MODE_PRIVATE)
        .getString("history", context.getString(R.string.no_events)) ?: ""
    @Synchronized fun clear(context: Context) { context.getSharedPreferences("events", Context.MODE_PRIVATE).edit().remove("history").apply() }
}
