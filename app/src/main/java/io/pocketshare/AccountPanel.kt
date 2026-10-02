package io.pocketshare

import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class AccountPanel(private val activity: AppCompatActivity, private val changed: () -> Unit) {
    private val store = ShareAccounts(activity)
    private fun text(id: Int) = activity.getString(id)
    fun bind() {
        activity.findViewById<View>(R.id.manage_accounts).setOnClickListener {
            activity.startActivity(Intent(activity, AccountsActivity::class.java))
        }
        refresh()
    }
    fun bindPage() {
        activity.findViewById<View>(R.id.add_account).setOnClickListener { edit(null) }
        refresh()
    }
    fun refresh() {
        val summary = activity.findViewById<TextView>(R.id.accounts_summary)
        summary.text = runCatching { store.load().let { activity.getString(R.string.accounts_count, it.count { u -> u.enabled }, it.size) } }
            .getOrElse { text(R.string.accounts_error) }
        activity.findViewById<LinearLayout>(R.id.account_list)?.let { showList(it) }
    }
    private fun showList(container: LinearLayout) {
        container.removeAllViews()
        val empty = activity.findViewById<TextView>(R.id.accounts_empty)
        val users = runCatching { store.load() }.getOrElse {
            empty.setText(R.string.accounts_error); empty.visibility = View.VISIBLE; return
        }
        empty.setText(R.string.accounts_empty)
        empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
        users.forEach { user ->
            val row = activity.layoutInflater.inflate(R.layout.account_row, container, false) as MaterialButton
            val permission = text(if (user.writable) R.string.account_rw else R.string.account_ro)
            val state = text(if (user.enabled) R.string.account_enabled else R.string.account_disabled)
            row.text = "${user.name}\n$state · $permission"
            SmoothCorners.apply(row)
            row.setOnClickListener { edit(user) }
            container.addView(row)
        }
    }
    private fun edit(user: ShareAccount?) {
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding / 3, padding, padding / 3) }
        fun field(label: Int, helper: Int, password: Boolean): TextInputEditText {
            val box = TextInputLayout(activity).apply {
                hint = text(label); helperText = text(helper)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = padding / 2
                }
                if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            }
            val input = TextInputEditText(box.context).apply {
                inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = true; isSaveEnabled = false
            }
            box.addView(input); content.addView(box); return input
        }
        val name = field(R.string.username, R.string.account_name_hint, false).apply { setText(user?.name.orEmpty()) }
        val password = field(R.string.password, if (user == null) R.string.account_password_new else R.string.account_password_keep, true)
        val enabled = MaterialSwitch(activity).apply { text = text(R.string.account_enabled); isChecked = user?.enabled ?: true }
        val write = MaterialSwitch(activity).apply { text = text(R.string.allow_write); isChecked = user?.writable ?: false }
        listOf(enabled, write).forEach {
            it.minimumHeight = (56 * activity.resources.displayMetrics.density).toInt()
            it.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        }
        content.addView(enabled); content.addView(write)
        content.addView(TextView(activity).apply {
            text = text(R.string.accounts_restart)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, padding / 2, 0, 0)
        })
        SmoothCorners.apply(content)
        val dialog = SmoothAlertDialogBuilder(activity).setTitle(if (user == null) R.string.account_add else R.string.account_edit)
            .setView(ScrollView(activity).apply { addView(content) }).setPositiveButton(R.string.save_settings, null)
            .setNegativeButton(R.string.cancel, null)
            .apply { if (user != null) setNeutralButton(R.string.account_delete) { _, _ ->
                SmoothAlertDialogBuilder(activity).setTitle(R.string.account_delete)
                    .setMessage(activity.getString(R.string.account_delete_confirm, user.name))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.account_delete) { _, _ ->
                        runCatching { store.remove(user) }.onSuccess { updated() }.onFailure { error() }
                    }.show()
            } }.create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim().lowercase(java.util.Locale.ROOT)
            val p = password.text.toString()
            if (!AccountPolicy.validName(n)) { name.error = text(R.string.account_name_hint); return@setOnClickListener }
            if ((user == null || p.isNotEmpty()) && !AccountPolicy.validPassword(p)) { password.error = text(R.string.account_password_new); return@setOnClickListener }
            runCatching { store.save(user, n, p, enabled.isChecked, write.isChecked) }
                .onSuccess { password.text?.clear(); dialog.dismiss(); updated() }.onFailure { error() }
        } }
        dialog.show()
    }
    private fun updated() { refresh(); changed(); Toast.makeText(activity, R.string.accounts_restart, Toast.LENGTH_LONG).show() }
    private fun error() { Toast.makeText(activity, R.string.accounts_error, Toast.LENGTH_LONG).show() }
}
