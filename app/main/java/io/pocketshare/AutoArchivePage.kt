package io.pocketshare

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class AutoArchivePage(private val activity: AppCompatActivity) {
    companion object { const val PICK_SOURCE = 51; const val PICK_OUTPUT = 52 }
    private val prefs = activity.getSharedPreferences(AutoArchiveService.PREFS, 0)
    private val controls = mutableListOf<View>()
    private lateinit var source: EditText
    private lateinit var output: EditText
    private var formatIndex = 0
    private var levelIndex = 1
    private lateinit var existing: CheckBox
    private lateinit var monitoring: MaterialSwitch
    private var updatingSwitch = false
    private var startingUntil = 0L
    private lateinit var status: TextView
    private lateinit var log: TextView
    private fun text(id: Int) = activity.getString(id)
    fun bind() {
        source = activity.findViewById(R.id.archive_source_path)
        output = activity.findViewById(R.id.archive_output_path)
        source.setText(prefs.getString("source", ""))
        output.setText(prefs.getString("destination", ""))
        listOf(source, output).forEach { field ->
            field.setOnFocusChangeListener { _, focused -> if (!focused) save() }
        }
        listOf(R.id.archive_pick_source to PICK_SOURCE, R.id.archive_pick_output to PICK_OUTPUT).forEach { (id, request) ->
            activity.findViewById<View>(id).setOnClickListener {
                save()
                activity.startActivityForResult(Intent(activity, DirectoryPickerActivity::class.java), request)
            }
        }
        formatIndex = AutoArchiveEngine.Format.entries.indexOfFirst { it.name == prefs.getString("format", "ZIP") }.coerceAtLeast(0)
        levelIndex = listOf(1, 3, 6).indexOf(prefs.getInt("level", 3)).coerceAtLeast(0)
        val formats = arrayOf("ZIP · Deflate", "TAR.XZ · LZMA2", "TAR.GZ · Gzip")
        val levels = arrayOf(text(R.string.archive_fast), text(R.string.archive_balanced), text(R.string.archive_small))
        fun choice(id: Int, title: Int, choices: Array<String>, index: () -> Int, update: (Int) -> Unit) {
            val button = activity.findViewById<MaterialButton>(id)
            button.text = choices[index()]
            button.setOnClickListener {
                SmoothAlertDialogBuilder(activity).setTitle(title)
                    .setSingleChoiceItems(choices, index()) { dialog, selected ->
                        if (!AutoArchiveService.running) {
                            update(selected)
                            button.text = choices[selected]
                            save()
                        }
                        dialog.dismiss()
                    }.setNegativeButton(R.string.cancel, null).show()
            }
        }
        choice(R.id.archive_format_choice, R.string.archive_format, formats, { formatIndex }) { formatIndex = it }
        choice(R.id.archive_level_choice, R.string.archive_level, levels, { levelIndex }) { levelIndex = it }
        existing = activity.findViewById(R.id.archive_existing)
        existing.isChecked = prefs.getBoolean("existing", false)
        existing.setOnCheckedChangeListener { _, _ -> save() }
        controls.addAll(listOf(source, output, existing))
        listOf(R.id.archive_pick_source, R.id.archive_pick_output, R.id.archive_format_choice, R.id.archive_level_choice).forEach { controls.add(activity.findViewById(it)) }
        status = activity.findViewById(R.id.archive_status)
        log = activity.findViewById(R.id.archive_log)
        val logToggle = activity.findViewById<MaterialButton>(R.id.archive_toggle_log)
        var expanded = prefs.getBoolean("log_expanded", false)
        fun renderLog() {
            log.visibility = if (expanded) View.VISIBLE else View.GONE
            logToggle.setIconResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
            androidx.core.view.ViewCompat.setStateDescription(logToggle, text(if (expanded) R.string.expanded else R.string.collapsed))
        }
        renderLog()
        logToggle.setOnClickListener { expanded = !expanded; prefs.edit().putBoolean("log_expanded", expanded).apply(); renderLog() }
        monitoring = activity.findViewById<MaterialSwitch>(R.id.archive_monitor).also { toggle ->
            toggle.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (!checked) {
                startingUntil = 0
                activity.stopService(Intent(activity, AutoArchiveService::class.java))
                toggle.postDelayed({ refresh() }, 300)
                return@setOnCheckedChangeListener
            }
            save()
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                refresh()
                Toast.makeText(activity, R.string.archive_permission, Toast.LENGTH_LONG).show()
                activity.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")))
            } else if (Build.VERSION.SDK_INT < 30 && activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                refresh()
                activity.requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 74)
            } else {
                startingUntil = android.os.SystemClock.elapsedRealtime() + 2000
                try { activity.startForegroundService(Intent(activity, AutoArchiveService::class.java)) }
                catch (error: Exception) {
                    startingUntil = 0
                    Toast.makeText(activity, activity.getString(R.string.archive_failed, error.message), Toast.LENGTH_LONG).show()
                }
                refresh()
                toggle.postDelayed({ refresh() }, 2200)
            }
            }
        }
        refresh()
    }
    fun save() {
        if (!::existing.isInitialized || AutoArchiveService.running) return
        prefs.edit().putString("source", source.text.toString()).putString("destination", output.text.toString())
            .putString("format", AutoArchiveEngine.Format.entries[formatIndex].name)
            .putInt("level", listOf(1, 3, 6)[levelIndex]).putBoolean("existing", existing.isChecked).apply()
    }
    fun result(request: Int, path: String) { (if (request == PICK_SOURCE) source else output).setText(path); save() }
    fun refresh() {
        val active = AutoArchiveService.running
        if (active) startingUntil = 0
        val starting = android.os.SystemClock.elapsedRealtime() < startingUntil
        controls.forEach { it.isEnabled = !active && !starting }
        updatingSwitch = true
        monitoring.isChecked = active || starting
        updatingSwitch = false
        status.text = text(if (active) R.string.archive_watching else R.string.archive_idle) + "\n" + prefs.getString("status", "")
        log.text = prefs.getString("log", "")
    }
}
