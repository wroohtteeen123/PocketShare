package io.pocketshare

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors

object PocketTheme {
    fun key(context: Context): String {
        val prefs = context.getSharedPreferences("settings", 0)
        return "${prefs.getInt("night_mode", -1)}:${prefs.getBoolean("dynamic_colors", false)}"
    }
    fun apply(activity: AppCompatActivity) {
        if (activity.getSharedPreferences("settings", 0).getBoolean("dynamic_colors", false)) {
            val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val overlay = if (night) com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
                else com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
            DynamicColors.applyToActivityIfAvailable(activity, DynamicColorsOptions.Builder().setThemeOverlay(overlay).build())
        }
    }
    fun systemBars(activity: AppCompatActivity, anchor: View) {
        SmoothCorners.apply(activity.findViewById(android.R.id.content))
        activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
        activity.window.navigationBarColor = MaterialColors.getColor(anchor, com.google.android.material.R.attr.colorSurface)
        val light = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        androidx.core.view.WindowCompat.getInsetsController(activity.window, anchor).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}
