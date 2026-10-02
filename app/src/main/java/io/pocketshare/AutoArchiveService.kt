package io.pocketshare

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

class AutoArchiveService : Service() {
    companion object {
        const val PREFS = "auto_archive"
        private const val CHANNEL = "auto_archive"
        private const val NOTIFICATION = 2102
        @Volatile var running = false
            private set
    }
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var stopping = false
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        running = true
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.archive_title), NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, notification(getString(R.string.archive_starting)))
        val source = prefs.getString("source", "").orEmpty()
        val destination = prefs.getString("destination", "").orEmpty()
        val format = prefs.getString("format", "ZIP").orEmpty()
        val level = prefs.getInt("level", 3)
        val existing = prefs.getBoolean("existing", false)
        worker.execute {
            try { watch(File(source), File(destination), AutoArchiveEngine.Format.valueOf(format), level, existing) }
            catch (error: Exception) {
                if (!stopping) report(getString(R.string.archive_failed, error.message ?: error.javaClass.simpleName))
            } finally { stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun watch(source: File, output: File, format: AutoArchiveEngine.Format, level: Int, existing: Boolean) {
        check(source.isAbsolute && output.isAbsolute && source.isDirectory && output.isDirectory) { getString(R.string.archive_paths_error) }
        check(AutoArchiveEngine.separate(source, output)) { getString(R.string.archive_overlap) }
        check(source.canRead() && output.canWrite()) { getString(R.string.archive_permission) }
        val identity = "${source.canonicalPath}\u0000${output.canonicalPath}\u0000$format\u0000$level"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val history = getSharedPreferences("archive_history_$key", MODE_PRIVATE)
        val pending = mutableMapOf<String, Pair<String, Long>>()
        val retryAt = mutableMapOf<String, Long>()
        val errors = mutableMapOf<String, String>()
        var first = true
        report(getString(R.string.archive_watching))
        while (!stopping && !Thread.currentThread().isInterrupted) {
            check(source.isDirectory && output.isDirectory) { getString(R.string.archive_paths_error) }
            val items = source.listFiles()?.sortedBy { it.name } ?: error(getString(R.string.archive_permission))
            val names = items.map { it.name }.toSet()
            pending.keys.retainAll(names); retryAt.keys.retainAll(names); errors.keys.retainAll(names)
            // A removed and later reintroduced item is a new arrival.
            history.all.keys.filter { it !in names }.forEach { history.edit().remove(it).apply() }
            for (item in items) {
                if (stopping || Thread.currentThread().isInterrupted) return
                try {
                    val snapshot = AutoArchiveEngine.snapshot(item)
                    val signature = snapshot.signature
                    if (first && !existing) { history.edit().putString(item.name, signature).apply(); continue }
                    if (history.getString(item.name, null) == signature) { pending.remove(item.name); continue }
                    val now = SystemClock.elapsedRealtime()
                    val previous = pending[item.name]
                    if (previous == null || previous.first != signature) {
                        pending[item.name] = signature to now
                        continue
                    }
                    if (now - previous.second < 30000 || now < (retryAt[item.name] ?: 0)) continue
                    report(getString(R.string.archive_working, item.name))
                    val result = AutoArchiveEngine.archive(item, output, format, level, signature)
                    history.edit().putString(item.name, signature).commit()
                    pending.remove(item.name); errors.remove(item.name)
                    report(getString(R.string.archive_complete, result.absolutePath))
                } catch (error: Exception) {
                    if (stopping || Thread.currentThread().isInterrupted) return
                    val detail = "${item.name}: ${error.message}"
                    if (errors.put(item.name, detail) != detail) report(getString(R.string.archive_failed, detail))
                    retryAt[item.name] = SystemClock.elapsedRealtime() + 60000
                }
            }
            first = false
            Thread.sleep(5000)
        }
    }
    private fun report(message: String) {
        if (stopping) return
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        prefs.edit().putString("status", message).putString("log", ("$time  $message\n" + prefs.getString("log", "")).take(10000)).apply()
        EventLog.append(this, message)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(message))
    }
    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 2102, Intent(this, MainActivity::class.java).putExtra("archive_page", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2103, Intent(this, AutoArchiveService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.archive_title)).setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message)).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.archive_stop), stop).build())
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    override fun onDestroy() {
        stopping = true
        worker.shutdownNow()
        running = false
        super.onDestroy()
    }
}
