package io.pocketshare

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Runs native Samba smbd (SMB2/SMB3), required by iOS Files. */
class SmbService : Service() {
    companion object {
        const val EXTRA_PORT = "port"; const val EXTRA_SHARE_PATH = "share_path"
        const val EXTRA_ALLOW_GUEST = "allow_guest"; const val EXTRA_ALLOW_WRITE = "allow_write"
        const val EXTRA_REQUIRE_ENCRYPTION = "require_encryption"
        const val ACTION_STATUS = "io.pocketshare.SAMBA_STATUS"; const val EXTRA_STATUS = "status"
        private const val CHANNEL = "samba_server"
        private const val BUNDLE_VERSION = "11"
    }
    private lateinit var stateDir: File
    private lateinit var startupLog: File
    private val startRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var notificationStatus = ""
    private fun ui(id: Int, vararg args: Any): String {
        val locales = if (Build.VERSION.SDK_INT >= 33)
            androidx.core.os.LocaleListCompat.wrap(getSystemService(LocaleManager::class.java).applicationLocales)
        else androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocales(if (locales.isEmpty) android.content.res.Resources.getSystem().configuration.locales
            else android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
        return createConfigurationContext(config).getString(id, *args)
    }
    @Volatile private var stopped = false
    @Volatile private var readyForMonitoring = false
    private val monitor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var tx = -1L
    private var rx = -1L
    private var sampledAt = 0L
    private var processCheckedAt = 0L
    private var processRunning = false
    private fun beginMonitoring() {
        monitor.scheduleWithFixedDelay({
            if (!stopped) runCatching {
                val now = android.os.SystemClock.elapsedRealtime()
                if (sampledAt != 0L && now - sampledAt < SpeedSettings.interval(this)) return@runCatching
                val nextTx = android.net.TrafficStats.getTotalTxBytes()
                val nextRx = android.net.TrafficStats.getTotalRxBytes()
                fun rate(current: Long, previous: Long): String {
                    if (current < 0 || previous < 0 || current < previous || now <= sampledAt) return "—"
                    val bytes = (current - previous) * 1000.0 / (now - sampledAt)
                    return if (bytes >= 1048576) "%.1f MB/s".format(bytes / 1048576) else "%.1f KB/s".format(bytes / 1024)
                }
                val speed = ui(R.string.notify_speed, rate(nextTx, tx), rate(nextRx, rx))
                tx = nextTx; rx = nextRx; sampledAt = now
                if (readyForMonitoring) {
                    if (processCheckedAt == 0L || now - processCheckedAt >= 5000L) {
                        processRunning = !SambaProcesses.snapshot(this)["bin/smbd"].isNullOrEmpty()
                        processCheckedAt = now
                    }
                    notificationStatus = if (!processRunning) ui(R.string.notify_stopped) else ui(R.string.notify_running, activePort)
                } else notificationStatus = ui(R.string.notify_starting)
                if (!stopped) getSystemService(NotificationManager::class.java).notify(7, notification("$notificationStatus\n$speed"))
            }
        }, 0, 1000, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    private var activePort = 4450

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startRequested.compareAndSet(false, true)) return START_NOT_STICKY
        startForeground(7, notification(ui(R.string.notify_starting_samba)))
        beginMonitoring()
        Thread({ startSamba(intent ?: Intent()) }, "pocketshare-samba").start()
        return START_NOT_STICKY
    }

    private fun startSamba(intent: Intent) {
        try {
        if (stopped) return
        if (!RootAccess.isAvailable()) return stopWith(ui(R.string.service_root))
        val smbd = findSmbd() ?: return stopWith(ui(R.string.service_bundle))
        stateDir = File(filesDir, "samba"); if (!stateDir.exists() && !stateDir.mkdirs()) return stopWith(ui(R.string.service_directory))
        val port = intent.getIntExtra(EXTRA_PORT, 4450)
        activePort = port
        val path = intent.getStringExtra(EXTRA_SHARE_PATH) ?: File(getExternalFilesDir(null), "share").absolutePath
        if (!DirectoryPath.isValid(path) || !RootAccess.runChecked("test -d ${DirectoryPath.shellQuote(path)}")) {
            return stopWith(ui(R.string.directory_unavailable))
        }
        val guest = intent.getBooleanExtra(EXTRA_ALLOW_GUEST, true); val write = intent.getBooleanExtra(EXTRA_ALLOW_WRITE, true)
        val encrypted = intent.getBooleanExtra(EXTRA_REQUIRE_ENCRYPTION, false)
        val accountStore = ShareAccounts(this)
        val accounts = accountStore.load()
        if (!guest && accounts.none { it.enabled }) return stopWith(ui(R.string.accounts_required))
        if (encrypted && guest) {
            return stopWith(ui(R.string.encryption_invalid))
        }
        writeConfig(path, port, guest, write, encrypted, accounts)
        // Rebuild the dedicated passdb each start: removed/disabled/legacy users cannot authenticate.
        val passdb = DirectoryPath.shellQuote(File(stateDir, "private/accounts.tdb").absolutePath)
        check(RootAccess.runChecked("umask 077; : > $passdb && chmod 600 $passdb")) { ui(R.string.accounts_error) }
        accounts.filter { it.enabled }.forEach { user -> createUser(smbd, user.name, accountStore.password(user)) }
        report(ui(if (encrypted) R.string.encryption_required_status else R.string.encryption_optional_status))
        report(ui(R.string.server_copy_status))
        val runtime = bundledRoot()
        val shell = runtimeEnvironment(runtime)
        val check = RootAccess.execute("${shell}'$smbd' -V")
        if (!check.succeeded) return stopWith(ui(R.string.service_library, diagnostic(check.output)))
        val command = "${shell}'$smbd' -D -s '${configFile().absolutePath}'"
        if (stopped) return
        val result = RootAccess.execute(command)
        if (stopped) { SambaProcesses.kill(this); return }
        if (!result.succeeded) return stopWith(ui(R.string.service_start, diagnostic(result.output)))
        val pidFile = File(stateDir, "run/smbd.pid").absolutePath
        var ready = false
        var processAlive = false
        for (attempt in 1..10) {
            Thread.sleep(500)
            processAlive = RootAccess.runChecked("test -s '${shell(pidFile)}' && kill -0 \$(cat '${shell(pidFile)}')")
            if (processAlive && runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
            }.isSuccess) {
                ready = true
                break
            }
        }
        if (ready) { readyForMonitoring = true; notificationStatus = ui(R.string.notify_running, port); report(ui(R.string.service_ready, port)) }
        else {
            val log = RootAccess.runForOutput("test -f '${shell(startupLog.absolutePath)}' && tail -n 80 '${shell(startupLog.absolutePath)}'")
            stopWith(ui(R.string.service_check, processAlive.toString(), port, diagnostic(log)))
        }
        } catch (error: Exception) { stopWith(ui(R.string.service_config, diagnostic(error.message ?: error.javaClass.simpleName))) }
    }

    private fun stopWith(message: String) { report(message); stopSelf() }
    private fun findSmbd(): String? {
        val configured = getSharedPreferences("settings", MODE_PRIVATE).getString("smbd_path", "")
        val bundled = runCatching { File(bundledRoot(), "bin/smbd").absolutePath }.getOrNull()
        return listOf(bundled, configured).firstOrNull { !it.isNullOrEmpty() && RootAccess.fileExists(it) }
    }
    private fun bundledRoot(): File {
        val destination = File(filesDir, "native-samba")
        val marker = File(destination, ".ready")
        if (!marker.exists() || marker.readText() != BUNDLE_VERSION) {
            destination.deleteRecursively()
            copyAssetTree("samba-arm64", destination)
            val escaped = destination.absolutePath.replace("'", "'\\\"'\\\"'")
            if (!RootAccess.runChecked("chmod -R 700 '$escaped/bin' '$escaped/lib' '$escaped/libexec'")) {
                throw IllegalStateException("Cannot mark bundled Samba executable")
            }
            restoreAssetLinks(destination)
            marker.writeText(BUNDLE_VERSION)
        }
        return destination
    }
    private fun copyAssetTree(source: String, destination: File) {
        val children = assets.list(source) ?: emptyArray()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(source).use { input -> FileOutputStream(destination).use { input.copyTo(it) } }
        } else {
            destination.mkdirs()
            children.forEach { copyAssetTree("$source/$it", File(destination, it)) }
        }
    }
    private fun restoreAssetLinks(destination: File) {
        assets.open("samba-arm64/samba-links.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val parts = line.split('\t', limit = 2)
                if (parts.size != 2) return@forEach
                val link = File(destination, parts[0].removePrefix("./"))
                link.parentFile?.mkdirs()
                val target = parts[1].replace("'", "'\\\"'\\\"'")
                val linkPath = link.absolutePath.replace("'", "'\\\"'\\\"'")
                RootAccess.runChecked("ln -sfn '$target' '$linkPath'")
            }
        }
    }
    private fun createUser(smbd: String, user: String, password: String) {
        val smbpasswd = smbd.replace("/sbin/smbd", "/bin/smbpasswd").replace("/bin/smbd", "/bin/smbpasswd")
        check(RootAccess.fileExists(smbpasswd)) { ui(R.string.service_account_tool) }
        check(AccountPolicy.validName(user) && AccountPolicy.validPassword(password)) { ui(R.string.accounts_error) }
        val result = RootAccess.executeWithInput("${runtimeEnvironment(bundledRoot())}'$smbpasswd' -c '${configFile().absolutePath}' -D 0 -s -a '${shell(user)}'", "$password\n$password\n")
        if (!result.succeeded) {
            val details = "smbpasswd user=$user exit=${result.exitCode ?: "unavailable"}\n" +
                AccountDiagnostics.sanitize(result.output, password)
            runCatching { startupLog.appendText("$details\n") }
            error(ui(R.string.account_provision_error, user) + "\n" + details)
        }
    }
    private fun runtimeEnvironment(runtime: File): String {
        val root = shell(runtime.absolutePath)
        return "export PREFIX='$root'; export LD_LIBRARY_PATH='$root/lib:$root/lib/samba'; " +
            "export POCKETSHARE_RUNTIME='$root'; export POCKETSHARE_STATE='${shell(stateDir.absolutePath)}'; " +
            "export POCKETSHARE_MODULES='$root/lib/samba'; " +
            "export LD_PRELOAD='$root/lib/libpocketshare-paths.so'; export PATH='$root/bin':\$PATH; "
    }
    private fun writeConfig(path: String, port: Int, guest: Boolean, write: Boolean, encrypted: Boolean, accounts: List<ShareAccount>) {
        listOf("private", "lock", "state", "cache", "run", "log").forEach { File(stateDir, it).mkdirs() }
        File(stateDir, "private/accounts.passwd").apply {
            writeText(accounts.joinToString("\n", postfix = "\n") { "${it.name}:${it.uid}" })
            setReadable(false, false); setReadable(true, true)
            setWritable(false, false); setWritable(true, true)
        }
        val root = stateDir.absolutePath
        startupLog = File(stateDir, "log/smbd-${System.currentTimeMillis()}.log")
        // Create the current attempt's log even if account provisioning fails before smbd starts.
        startupLog.writeText("PocketShare: preparing Samba configuration\n")
        startupLog.setReadable(false, false); startupLog.setReadable(true, true)
        startupLog.setWritable(false, false); startupLog.setWritable(true, true)
        val config = """[global]
workgroup = WORKGROUP
server string = PocketShare Samba
${SmbTransportPolicy.config(guest, encrypted)}
# Local Android writers do not participate in Samba directory lease breaks.
# Keep file leases; directory caching is opt-in and disabled by default.
smb3 directory leases = ${if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("directory_cache", false)) "yes" else "no"}
kernel change notify = yes
change notify = yes
smb ports = $port
disable netbios = yes
bind interfaces only = no
${AccountPolicy.guestMapping(guest)}
guest account = root
passdb backend = tdbsam:$root/private/accounts.tdb
ncalrpc dir = $root/run/ncalrpc
private dir = $root/private
lock directory = $root/lock
state directory = $root/state
cache directory = $root/cache
pid directory = $root/run
log file = ${startupLog.absolutePath}
max log size = 256
log level = 3

[Share]
path = ${path.replace("\n", "")}
browseable = yes
${AccountPolicy.config(accounts, guest, write)}
guest ok = ${if (guest) "yes" else "no"}
force user = root
"""
        configFile().writeText(config)
    }
    private fun configFile() = File(stateDir, "smb.conf")
    private fun shell(value: String) = value.replace("'", "'\\\"'\\\"'")
    private fun diagnostic(value: String) = value.trim().takeLast(12000).ifBlank { ui(R.string.service_empty) }
    private fun report(message: String) { EventLog.append(this, message); getSharedPreferences("settings", MODE_PRIVATE).edit().putString("samba_status", message).apply(); sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, message)) }
    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { manager.createNotificationChannel(NotificationChannel(CHANNEL, "Samba server", NotificationManager.IMPORTANCE_LOW)); Notification.Builder(this, CHANNEL) } else Notification.Builder(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(ui(R.string.notify_title))
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOnlyAlertOnce(true).setOngoing(true).build()
    }
    override fun onDestroy() {
        stopped = true
        monitor.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Thread({ SambaProcesses.kill(this) }, "pocketshare-stop").start()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
