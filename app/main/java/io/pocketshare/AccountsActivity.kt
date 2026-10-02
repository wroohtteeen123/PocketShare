package io.pocketshare

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

/** Separate management destination; returning never starts or stops sharing. */
class AccountsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PocketTheme.apply(this)
        setContentView(R.layout.activity_accounts)
        PocketTheme.systemBars(this, findViewById(R.id.back_accounts))
        findViewById<View>(R.id.back_accounts).setOnClickListener { finish() }
        AccountPanel(this) {}.bindPage()
    }
}
