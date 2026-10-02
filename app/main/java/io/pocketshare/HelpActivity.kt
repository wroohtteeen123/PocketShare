package io.pocketshare

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

/** A normal full-screen destination, with Android back-stack behavior. */
class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PocketTheme.apply(this)
        setContentView(R.layout.activity_help)
        PocketTheme.systemBars(this, findViewById(R.id.back_help))
        findViewById<View>(R.id.back_help).setOnClickListener { finish() }
        AppearanceSettings(this) { true }.bindPanel(findViewById(android.R.id.content))
    }
}
