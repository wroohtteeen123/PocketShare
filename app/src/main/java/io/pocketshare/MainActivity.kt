package io.pocketshare

import android.content.*
import android.graphics.Bitmap
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

/** Native Android configuration screen for the SMB foreground service. */
class MainActivity : AppCompatActivity() {
    companion object { private const val PICK_DIRECTORY = 42; private const val SMB_STANDARD_PORT = 445; private const val DEFAULT_NON_ROOT_PORT = 4450; private const val LEGACY_REDIRECT_PORT = 4450 }
    private lateinit var port: EditText
    private lateinit var rootMode: CheckBox; private lateinit var guestAccess: CheckBox; private lateinit var allowWrite: CheckBox
    private lateinit var requireEncryption: CheckBox
    private lateinit var directoryCache: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var ipAddress: TextView; private lateinit var directory: EditText; private lateinit var connection: TextView; private lateinit var status: TextView
    private lateinit var qrCode: ImageView; private lateinit var logs: TextView; private lateinit var uploadSpeed: TextView; private lateinit var downloadSpeed: TextView
    private var lastQrEndpoint: String? = null
    private var lastQrInk: Int? = null
    private var currentEndpoint: String? = null
    private var refreshingLogs = false
    private var refreshingComponents = false
    private var presentationKey = ""
    private lateinit var appearanceSettings: AppearanceSettings
    private lateinit var archivePage: AutoArchivePage
    private lateinit var accountPanel: AccountPanel
    private fun refreshComponents() {
        if (refreshingComponents) return
        refreshingComponents = true
        Thread({
            val result = runCatching { SambaProcesses.snapshot(this) }
            runOnUiThread {
                refreshingComponents = false
                if (isDestroyed) return@runOnUiThread
                val panel = findViewById<LinearLayout>(R.id.component_status)
                panel.removeAllViews()
                if (result.isFailure) {
                    panel.addView(TextView(this).apply { text = getString(R.string.component_error) })
                    return@runOnUiThread
                }
                SambaProcesses.components.forEach { (key, defaultLabel) ->
                    val label = if (resources.configuration.locales[0].language == "zh") defaultLabel else key.substringAfterLast('/')
                    val pids = result.getOrThrow()[key].orEmpty()
                    panel.addView(TextView(this).apply { text = "$label\n" + if (pids.isEmpty()) getString(R.string.not_running) else getString(R.string.component_running, pids.joinToString()); setPadding(0, 16, 0, 0) })
                    panel.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = getString(R.string.force_component, label); isEnabled = pids.isNotEmpty()
                        SmoothCorners.apply(this)
                        setOnClickListener {
                            SmoothAlertDialogBuilder(this@MainActivity).setTitle(getString(R.string.force_stop_title))
                                .setMessage(getString(R.string.force_message, label))
                                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.force_stop)) { _, _ ->
                                    isEnabled = false
                                    if (key == "bin/smbd") stopService(Intent(this@MainActivity, SmbService::class.java))
                                    Thread({
                                        val killed = SambaProcesses.kill(this@MainActivity, key)
                                        EventLog.append(this@MainActivity, if (killed.succeeded) getString(R.string.kill_requested, label) else getString(R.string.kill_failed, label, killed.output))
                                        runOnUiThread { if (!isDestroyed) { refreshComponents(); logs.text = EventLog.read(this@MainActivity) } }
                                    }, "pocketshare-kill").start()
                                }.show()
                        }
                    })
                }
            }
        }, "pocketshare-components").start()
    }
    private lateinit var preferences: SharedPreferences; private lateinit var sharePath: String
    private val speedHandler = Handler(Looper.getMainLooper()); private var lastTx = -1L; private var lastRx = -1L; private var lastTime = 0L
    private fun refreshRuntimeLog() {
        refreshComponents()
        if (refreshingLogs) return
        refreshingLogs = true
        findViewById<View>(R.id.refresh_logs).isEnabled = false
        findViewById<TextView>(R.id.log_refresh_status).text = getString(R.string.reading_logs)
        logs.text = EventLog.read(this)
        Thread({
            val result = runCatching {
                val latest = File(filesDir, "samba/log").listFiles()
                    ?.filter { it.name.startsWith("smbd-") && it.extension == "log" }
                    ?.maxByOrNull { it.name }
                if (latest == null) getString(R.string.no_diagnostics)
                else {
                    val output = RootAccess.execute("tail -n 160 '${latest.absolutePath}'")
                    if (!output.succeeded) error(getString(R.string.root_retry))
                    output.output.ifBlank { getString(R.string.empty_diagnostics) }
                }
            }
            runOnUiThread {
                refreshingLogs = false
                if (!isDestroyed) {
                    findViewById<View>(R.id.refresh_logs).isEnabled = true
                    findViewById<TextView>(R.id.runtime_logs).text = result.getOrElse { getString(R.string.read_log_error) }
                    val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
                    findViewById<TextView>(R.id.log_refresh_status).text =
                        if (result.isSuccess) getString(R.string.updated_at, time) else getString(R.string.log_refresh_error)
                }
            }
        }, "pocketshare-log-reader").start()
    }
    private fun setupCollapsibleSection(buttonId: Int, contentId: Int, title: String, key: String, defaultExpanded: Boolean, preferencePrefix: String = "logs") {
        val button = findViewById<MaterialButton>(buttonId)
        val content = findViewById<View>(contentId)
        var expanded = preferences.getBoolean("${preferencePrefix}_expanded_$key", defaultExpanded)
        fun render() {
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            button.text = title
            button.setIconResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            button.contentDescription = "$title，${if (expanded) getString(R.string.collapse) else getString(R.string.expand)}"
            ViewCompat.setStateDescription(button, if (expanded) getString(R.string.expanded) else getString(R.string.collapsed))
        }
        render()
        button.setOnClickListener {
            expanded = !expanded
            preferences.edit().putBoolean("${preferencePrefix}_expanded_$key", expanded).apply()
            render()
        }
    }
    private fun copyText(label: String, value: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, getString(R.string.copied, label), Toast.LENGTH_SHORT).show()
    }
    private val speedTick = object : Runnable { override fun run() { if (::archivePage.isInitialized) archivePage.refresh(); val tx = android.net.TrafficStats.getTotalTxBytes(); val rx = android.net.TrafficStats.getTotalRxBytes(); val now = SystemClock.elapsedRealtime(); uploadSpeed.text = formatSpeed(tx, lastTx, now - lastTime); downloadSpeed.text = formatSpeed(rx, lastRx, now - lastTime); lastTx = tx; lastRx = rx; lastTime = now; speedHandler.postDelayed(this, SpeedSettings.interval(this@MainActivity)) } }
    private val sambaStatus = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { status.text = intent.getStringExtra(SmbService.EXTRA_STATUS) ?: ""; logs.text = EventLog.read(context) } }

    override fun onCreate(state: Bundle?) {
        preferences = getSharedPreferences("settings", MODE_PRIVATE)
        super.onCreate(state)
        PocketTheme.apply(this)
        presentationKey = PocketTheme.key(this)
        setContentView(R.layout.activity_main)
        PocketTheme.systemBars(this, findViewById(R.id.open_help))
        setupCollapsibleSection(R.id.toggle_components, R.id.components_content, getString(R.string.components_title), "components", false)
        setupCollapsibleSection(R.id.toggle_records, R.id.records_content, getString(R.string.records_title), "records", false)
        setupCollapsibleSection(R.id.toggle_events, R.id.events_content, getString(R.string.events_title), "events", true)
        setupCollapsibleSection(R.id.toggle_diagnostics, R.id.diagnostics_content, getString(R.string.diagnostics_title), "diagnostics", false)
        setupCollapsibleSection(R.id.toggle_settings_directory, R.id.settings_directory_content, getString(R.string.directory_title), "directory", false, "settings")
        setupCollapsibleSection(R.id.toggle_settings_network, R.id.settings_network_content, getString(R.string.network_title), "network", false, "settings")
        setupCollapsibleSection(R.id.toggle_settings_access, R.id.settings_access_content, getString(R.string.access_title), "access", false, "settings")
        setupCollapsibleSection(R.id.toggle_settings_permissions, R.id.settings_permissions_content, getString(R.string.permissions_title), "permissions", false, "settings")
        findViewById<View>(R.id.refresh_components).setOnClickListener { refreshComponents() }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 73)
        }
        port = findViewById(R.id.port); rootMode = findViewById(R.id.root_mode); guestAccess = findViewById(R.id.guest_access); allowWrite = findViewById(R.id.allow_write)
        ipAddress = findViewById(R.id.ip_address); directory = findViewById(R.id.share_directory); connection = findViewById(R.id.connection); status = findViewById(R.id.status); qrCode = findViewById(R.id.qr_code); logs = findViewById(R.id.logs); uploadSpeed = findViewById(R.id.upload_speed); downloadSpeed = findViewById(R.id.download_speed)
        requireEncryption = findViewById(R.id.require_encryption)
        directoryCache = findViewById(R.id.directory_cache)
        directoryCache.isChecked = preferences.getBoolean("directory_cache", false)
        directoryCache.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean("directory_cache", checked).apply()
            feedback(getString(R.string.directory_cache_hint))
        }
        sharePath = preferences.getString("share_path", defaultSharePath()) ?: defaultSharePath(); port.setText(preferences.getInt("port", DEFAULT_NON_ROOT_PORT).toString()); rootMode.isChecked = preferences.getBoolean("root_mode", false); guestAccess.isChecked = preferences.getBoolean("guest_access", true); allowWrite.isChecked = preferences.getBoolean("allow_write", true); status.text = preferences.getString("samba_status", getString(R.string.root_required))
        requireEncryption.isChecked = preferences.getBoolean("require_encryption", false) && !guestAccess.isChecked
        directory.setText(sharePath)
        directory.doAfterTextChanged {
            sharePath = it.toString()
            findViewById<TextInputLayout>(R.id.directory_input).error = null
            feedback(getString(R.string.settings_changed))
        }
        findViewById<BottomNavigationView>(R.id.navigation).setOnItemSelectedListener { item -> if (item.itemId != R.id.nav_settings && item.itemId != R.id.nav_archive && !saveSettings()) return@setOnItemSelectedListener false; if (::archivePage.isInitialized) archivePage.save(); refreshConnection(); val selected = item.itemId; findViewById<View>(R.id.home_page).visibility = if (selected == R.id.nav_home) View.VISIBLE else View.GONE; findViewById<View>(R.id.settings_page).visibility = if (selected == R.id.nav_settings) View.VISIBLE else View.GONE; findViewById<View>(R.id.logs_page).visibility = if (selected == R.id.nav_logs) View.VISIBLE else View.GONE; findViewById<View>(R.id.archive_page).visibility = if (selected == R.id.nav_archive) View.VISIBLE else View.GONE; logs.text = EventLog.read(this); if (selected == R.id.nav_logs) refreshRuntimeLog(); true }
        findViewById<View>(R.id.clear_logs).setOnClickListener { EventLog.clear(this); logs.text = EventLog.read(this); Toast.makeText(this, getString(R.string.events_cleared), Toast.LENGTH_SHORT).show() }; findViewById<View>(R.id.select_directory).setOnClickListener { selectDirectory() }; findViewById<View>(R.id.start).setOnClickListener { startSharing() }; findViewById<View>(R.id.stop).setOnClickListener { stopSharing() }
        findViewById<View>(R.id.copy_connection).setOnClickListener {
            refreshConnection()
            currentEndpoint?.let { copyText(getString(R.string.endpoint_label), it) }
        }
        findViewById<View>(R.id.refresh_logs).setOnClickListener { refreshRuntimeLog() }
        findViewById<View>(R.id.copy_logs).setOnClickListener {
            copyText(getString(R.string.nav_logs), "${getString(R.string.events_title)}\n${logs.text}\n\n${getString(R.string.diagnostics_title)}\n${findViewById<TextView>(R.id.runtime_logs).text}")
        }
        findViewById<View>(R.id.save_settings).setOnClickListener {
            if (saveSettings()) {
                refreshConnection()
                feedback(getString(R.string.settings_saved))
            }
        }
        findViewById<View>(R.id.default_directory).setOnClickListener {
            sharePath = defaultSharePath()
            directory.setText(sharePath)
            preferences.edit().putString("share_path", sharePath).apply()
            refreshConnection()
            feedback(getString(R.string.default_selected))
        }
        findViewById<View>(R.id.reset_settings).setOnClickListener {
            SmoothAlertDialogBuilder(this).setTitle(getString(R.string.reset_title))
                .setMessage(getString(R.string.reset_message))
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.reset_confirm)) { _, _ ->
                    port.setText(DEFAULT_NON_ROOT_PORT.toString()); rootMode.isChecked = false
                    requireEncryption.isChecked = false
                    directoryCache.isChecked = false
                    guestAccess.isChecked = true; allowWrite.isChecked = true
                    sharePath = defaultSharePath()
                    directory.setText(sharePath)
                    saveSettings(); updateSettingControls(); refreshConnection()
                    feedback(getString(R.string.reset_done))
                }.show()
        }
        rootMode.setOnCheckedChangeListener { _, _ -> settingsChanged() }
        guestAccess.setOnCheckedChangeListener { _, _ -> settingsChanged() }
        allowWrite.setOnCheckedChangeListener { _, _ -> settingsChanged() }
        port.doAfterTextChanged { settingsChanged() }
        updateSettingControls(); refreshConnection()
        accountPanel = AccountPanel(this) { updateSettingControls() }.also { it.bind() }
        appearanceSettings = AppearanceSettings(this, ::saveSettings).also { it.bind() }
        setupSpeedControl()
        archivePage = AutoArchivePage(this).also { it.bind() }
        if (intent.getBooleanExtra("archive_page", false)) findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.nav_archive
        requireEncryption.setOnCheckedChangeListener { _, _ -> settingsChanged() }
        if (state != null) findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = state.getInt("selected_page", R.id.nav_home)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_page", findViewById<BottomNavigationView>(R.id.navigation).selectedItemId)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (presentationKey != PocketTheme.key(this)) { recreate(); return }
        if (::appearanceSettings.isInitialized) appearanceSettings.refreshPermissions()
        if (::accountPanel.isInitialized) { accountPanel.refresh(); updateSettingControls() }
    }
    private fun setupSpeedControl() {
        val button = findViewById<MaterialButton>(R.id.choose_speed_interval)
        fun refresh() {
            button.text = getString(R.string.speed_interval_value, SpeedSettings.interval(this) / 1000)
            findViewById<TextView>(R.id.speed_caption).text = SpeedSettings.caption(this)
        }
        refresh()
        button.setOnClickListener {
            val choices = SpeedSettings.intervals.map { getString(R.string.speed_interval_value, it / 1000) }.toTypedArray()
            SmoothAlertDialogBuilder(this).setTitle(R.string.speed_settings_title)
                .setSingleChoiceItems(choices, SpeedSettings.intervals.indexOf(SpeedSettings.interval(this))) { dialog, selected ->
                    preferences.edit().putLong(SpeedSettings.KEY, SpeedSettings.intervals[selected]).apply()
                    refresh()
                    speedHandler.removeCallbacks(speedTick)
                    speedHandler.post(speedTick)
                    dialog.dismiss()
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }
    override fun onStart() { super.onStart(); refreshConnection(); logs.text = EventLog.read(this); lastTx = -1; lastRx = -1; speedHandler.post(speedTick); ContextCompat.registerReceiver(this, sambaStatus, IntentFilter(SmbService.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED) }
    override fun onStop() { speedHandler.removeCallbacks(speedTick); saveSettings(); if (::archivePage.isInitialized) archivePage.save(); unregisterReceiver(sambaStatus); super.onStop() }
    private fun feedback(message: String) { findViewById<TextView>(R.id.settings_feedback).text = message }
    private fun settingsChanged() {
        updateSettingControls()
        feedback(getString(R.string.settings_changed))
        refreshConnection()
    }
    private fun updateSettingControls() {
        requireEncryption.isEnabled = !guestAccess.isChecked
        guestAccess.isEnabled = !requireEncryption.isChecked
        findViewById<TextInputLayout>(R.id.port_input).isEnabled = !rootMode.isChecked
        allowWrite.isEnabled = guestAccess.isChecked
        if (rootMode.isChecked) findViewById<TextInputLayout>(R.id.port_input).error = null
        findViewById<TextView>(R.id.access_summary).text = getString(R.string.accounts_access_hint)
    }
    private fun validateSettings(requirePassword: Boolean = false): Boolean {
        val validDirectory = DirectoryPath.isValid(directory.text.toString())
        findViewById<TextInputLayout>(R.id.directory_input).error =
            if (validDirectory) null else getString(R.string.directory_path_invalid)
        if (!validDirectory) { feedback(getString(R.string.invalid_settings)); return false }
        sharePath = directory.text.toString()
        if (requireEncryption.isChecked && guestAccess.isChecked) { feedback(getString(R.string.encryption_invalid)); return false }
        val validPort = rootMode.isChecked || port.text.toString().toIntOrNull() in 1024..65535
        val validUser = !requirePassword || guestAccess.isChecked || runCatching { ShareAccounts(this).load().any { it.enabled } }.getOrDefault(false)
        findViewById<TextInputLayout>(R.id.port_input).error = if (validPort) null else getString(R.string.invalid_port)
        if (!validUser) feedback(getString(R.string.accounts_required))
        else if (!validPort) feedback(getString(R.string.invalid_settings))
        return validPort && validUser
    }
    private fun saveSettings(): Boolean {
        if (!validateSettings()) return false
        val value = port.text.toString().toIntOrNull()?.takeIf { it in 1024..65535 }
            ?: preferences.getInt("port", DEFAULT_NON_ROOT_PORT)
        preferences.edit().putInt("port", value).putBoolean("root_mode", rootMode.isChecked)
            .putBoolean("guest_access", guestAccess.isChecked).putBoolean("allow_write", allowWrite.isChecked)
            .putBoolean("require_encryption", requireEncryption.isChecked)
            .putString("share_path", sharePath).apply()
        return true
    }
    private fun showStatus(message: String) { status.text = message; preferences.edit().putString("samba_status", message).apply(); EventLog.append(this, message); logs.text = EventLog.read(this) }
    private fun selectDirectory() { startActivityForResult(Intent(this, DirectoryPickerActivity::class.java), PICK_DIRECTORY) }
    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in listOf(AutoArchivePage.PICK_SOURCE, AutoArchivePage.PICK_OUTPUT)) {
            if (resultCode == RESULT_OK) data?.getStringExtra(DirectoryPickerActivity.EXTRA_PATH)?.let { archivePage.result(requestCode, it) }
            return
        }
        if (requestCode != PICK_DIRECTORY || resultCode != RESULT_OK) return
        val selected = data?.getStringExtra(DirectoryPickerActivity.EXTRA_PATH)?.takeIf(StorageDirectories::accepts) ?: return
        sharePath = selected
        directory.setText(selected)
        preferences.edit().putString("share_path", sharePath).apply()
        refreshConnection()
        feedback(getString(R.string.directory_path_saved))
    }
    private fun startSharing() {
        if (!validateSettings(requirePassword = true)) {
            findViewById<BottomNavigationView>(R.id.navigation).selectedItemId = R.id.nav_settings
            showStatus(getString(R.string.fix_settings))
            return
        }
        val selectedPort = readPort()
        val guest = guestAccess.isChecked
        if (!RootAccess.isAvailable()) return showStatus(getString(R.string.root_authorize))
        val useRoot = rootMode.isChecked; val listenerPort = if (useRoot) { RootAccess.disableStandardPort(LEGACY_REDIRECT_PORT); SMB_STANDARD_PORT } else selectedPort
        preferences.edit().putInt("port", selectedPort).putBoolean("root_mode", useRoot).putBoolean("guest_access", guest).putBoolean("allow_write", allowWrite.isChecked).apply()
        val intent = Intent(this, SmbService::class.java).putExtra(SmbService.EXTRA_REQUIRE_ENCRYPTION, requireEncryption.isChecked).putExtra(SmbService.EXTRA_PORT, listenerPort).putExtra(SmbService.EXTRA_SHARE_PATH, sharePath).putExtra(SmbService.EXTRA_ALLOW_GUEST, guest).putExtra(SmbService.EXTRA_ALLOW_WRITE, allowWrite.isChecked)
        startForegroundService(intent)
        refreshConnection(); showStatus(getString(R.string.starting))
    }
    private fun stopSharing() { stopService(Intent(this, SmbService::class.java)); RootAccess.disableStandardPort(LEGACY_REDIRECT_PORT); showStatus(getString(R.string.stopped)) }
    private fun refreshConnection() {
        val ip = localIp()
        val validPort = rootMode.isChecked || port.text.toString().toIntOrNull() in 1024..65535
        val endpoint = "smb://$ip" + (if (rootMode.isChecked) "" else ":${readPort()}") + "/Share"
        val available = ip != getString(R.string.no_wifi) && validPort
        val label = if (available) getString(R.string.endpoint_value, endpoint) else if (!validPort) getString(R.string.endpoint_invalid) else getString(R.string.endpoint_offline)
        ipAddress.text = getString(R.string.local_ip, ip)
        currentEndpoint = if (available) endpoint else null
        findViewById<View>(R.id.copy_connection).isEnabled = available
        connection.text = if (available) endpoint else label
        findViewById<TextView>(R.id.settings_endpoint).text = label
        qrCode.visibility = if (available) View.VISIBLE else View.GONE
        val qrInk = MaterialColors.getColor(qrCode, com.google.android.material.R.attr.colorPrimary)
        if (available && (endpoint != lastQrEndpoint || qrInk != lastQrInk)) {
            val bitmap = createQr(endpoint, qrInk)
            qrCode.setImageBitmap(bitmap)
            if (bitmap != null) {
                lastQrEndpoint = endpoint
                lastQrInk = qrInk
            }
        }
    }
    private fun readPort() = port.text.toString().toIntOrNull() ?: DEFAULT_NON_ROOT_PORT
    private fun defaultSharePath() = File(getExternalFilesDir(null), "share").absolutePath
    private fun localIp(): String = try { Collections.list(NetworkInterface.getNetworkInterfaces()).firstNotNullOfOrNull { network -> if (!network.isUp || network.isLoopback) null else Collections.list(network.inetAddresses).firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress } ?: getString(R.string.no_wifi) } catch (_: Exception) { getString(R.string.no_wifi) }
    private fun createQr(value: String, ink: Int): Bitmap? = try {
        // Resolve the theme color at the call site; keep the quiet zone transparent.
        Bitmap.createBitmap(QrPixels.encode(value, ink), QrPixels.SIZE, QrPixels.SIZE, Bitmap.Config.ARGB_8888)
    } catch (_: Exception) { null }
    private fun formatSpeed(current: Long, previous: Long, elapsed: Long): String { if (current < 0 || previous < 0 || current < previous || elapsed <= 0) return "—"; var value = (current - previous) * 1000.0 / elapsed; var unit = "B/s"; if (value >= 1024) { value /= 1024; unit = "KB/s" }; if (value >= 1024) { value /= 1024; unit = "MB/s" }; return String.format(Locale.getDefault(), "%.1f %s", value, unit) }
}
