package io.pocketshare

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch

/** Presentation preferences are independent of the running Samba configuration. */
class AppearanceSettings(private val activity: AppCompatActivity, private val saveSharing: () -> Boolean) {
    private val prefs = activity.getSharedPreferences("settings", 0)
    private val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
    private fun text(id: Int, vararg args: Any) = activity.getString(id, *args)

    fun bindPanel(root: View) {
        val themes = arrayOf(text(R.string.system_default), text(R.string.theme_light), text(R.string.theme_dark))
        val themeIndex = modes.indexOf(prefs.getInt("night_mode", modes[0])).coerceAtLeast(0)
        root.findViewById<MaterialButton>(R.id.choose_theme).apply {
            text = text(R.string.theme_value, themes[themeIndex])
            setOnClickListener {
                SmoothAlertDialogBuilder(activity).setTitle(R.string.theme_title)
                    .setSingleChoiceItems(themes, themeIndex) { dialog, selected ->
                        dialog.dismiss()
                        if (selected != themeIndex && saveSharing()) {
                            prefs.edit().putInt("night_mode", modes[selected]).apply()
                            AppCompatDelegate.setDefaultNightMode(modes[selected])
                            activity.recreate()
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            }
        }
        root.findViewById<MaterialSwitch>(R.id.dynamic_colors).apply {
            isEnabled = DynamicColors.isDynamicColorAvailable()
            isChecked = isEnabled && prefs.getBoolean("dynamic_colors", false)
            if (!isEnabled) root.findViewById<TextView>(R.id.dynamic_caption).setText(R.string.dynamic_unavailable)
            setOnCheckedChangeListener { _, checked ->
                if (saveSharing()) {
                    prefs.edit().putBoolean("dynamic_colors", checked).apply()
                    activity.recreate()
                } else isChecked = prefs.getBoolean("dynamic_colors", false)
            }
        }
        val tags = arrayOf("", "zh-CN", "en")
        val languages = arrayOf(text(R.string.system_default), "简体中文", "English")
        val language = AppCompatDelegate.getApplicationLocales()[0]?.language
        var languageIndex = when (language) { "zh" -> 1; "en" -> 2; else -> 0 }
        root.findViewById<MaterialButton>(R.id.choose_language).apply {
            text = text(R.string.language_value, languages[languageIndex])
            setOnClickListener {
                SmoothAlertDialogBuilder(activity).setTitle(R.string.language_title)
                    .setSingleChoiceItems(languages, languageIndex) { dialog, selected ->
                        dialog.dismiss()
                        if (selected != languageIndex && saveSharing()) {
                            languageIndex = selected
                            text = text(R.string.language_value, languages[selected])
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[selected]))
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
            }
        }
        val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "—"
        root.findViewById<TextView>(R.id.app_version).text = text(R.string.app_version, version)
        root.findViewById<MaterialButton>(R.id.connection_help).setOnClickListener {
            SmoothAlertDialogBuilder(activity).setTitle(R.string.help_title).setMessage(R.string.help_body)
                .setPositiveButton(R.string.done, null).show()
        }
        root.findViewById<MaterialButton>(R.id.open_licenses).setOnClickListener {
            SmoothAlertDialogBuilder(activity).setTitle(R.string.licenses_title)
                .setItems(arrayOf("GNU GPL v3 · Samba", "Apache 2.0 · AndroidX / Material / ZXing / Kotlin / Commons", text(R.string.native_inventory), "XZ for Java · 0BSD", "Apache Commons · NOTICE")) { _, index ->
                    val file = arrayOf("GPL-3.0.txt", "Apache-2.0.txt", "native-components.txt", "XZ-Java.txt", "Commons-NOTICE.txt")[index]
                    val content = activity.assets.open("licenses/$file").bufferedReader().use { it.readText() }
                    val scroll = ScrollView(activity)
                    scroll.addView(TextView(activity).apply {
                        this.text = content; textSize = 13f; setTextIsSelectable(true)
                        val padding = (24 * resources.displayMetrics.density).toInt()
                        setPadding(padding, padding, padding, padding)
                    })
                    SmoothAlertDialogBuilder(activity).setTitle(R.string.licenses_title).setView(scroll)
                        .setPositiveButton(R.string.done, null).show()
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    fun bind() {
        activity.findViewById<MaterialButton>(R.id.notification_settings).setOnClickListener {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
        }
        activity.findViewById<MaterialButton>(R.id.storage_settings).setOnClickListener {
            open(if (Build.VERSION.SDK_INT >= 30) Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")) else appDetails())
        }
        activity.findViewById<MaterialButton>(R.id.battery_settings).setOnClickListener {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

        activity.findViewById<View>(R.id.open_help).setOnClickListener {
            if (saveSharing()) activity.startActivity(Intent(activity, HelpActivity::class.java))
        }
        refreshPermissions()
    }


    fun refreshPermissions() {
        val manager = activity.getSystemService(android.app.NotificationManager::class.java)
        val notifications = NotificationManagerCompat.from(activity).areNotificationsEnabled() &&
            manager.getNotificationChannel("samba_server")?.importance != android.app.NotificationManager.IMPORTANCE_NONE
        val storage = if (Build.VERSION.SDK_INT < 30) R.string.not_required else if (Environment.isExternalStorageManager()) R.string.enabled else R.string.disabled
        val exempt = activity.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(activity.packageName)
        activity.findViewById<TextView>(R.id.permission_summary).text = text(R.string.permission_summary,
            text(if (notifications) R.string.enabled else R.string.disabled), text(storage),
            text(if (exempt) R.string.battery_exempt else R.string.battery_optimized))
    }

    private fun appDetails() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
    private fun open(intent: Intent) {
        if (runCatching { activity.startActivity(intent) }.isFailure && runCatching { activity.startActivity(appDetails()) }.isFailure) {
            Toast.makeText(activity, R.string.settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
