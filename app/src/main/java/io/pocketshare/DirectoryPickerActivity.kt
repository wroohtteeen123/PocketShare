package io.pocketshare

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import java.util.concurrent.Executors

class DirectoryPickerActivity : AppCompatActivity() {
    companion object { const val EXTRA_PATH = "directory_path" }
    private var current = StorageDirectories.ROOT
    private var generation = 0
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var list: ListView
    private lateinit var header: View
    private lateinit var message: TextView
    private lateinit var path: TextView
    private lateinit var choose: MaterialButton
    private lateinit var progress: ProgressBar
    private var entries = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PocketTheme.apply(this)
        setContentView(R.layout.activity_directory_picker)
        PocketTheme.systemBars(this, findViewById(R.id.picker_back))
        list = findViewById(R.id.picker_list)
        header = layoutInflater.inflate(R.layout.directory_picker_header, list, false)
        SmoothCorners.apply(header)
        list.addHeaderView(header, null, false)
        list.adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.directory_picker_row, parent, false)
                val destination = getItem(position)
                row.findViewById<TextView>(R.id.picker_folder).apply {
                    text = destination.substringAfterLast('/')
                    setOnClickListener { load(destination) }
                }
                return row
            }
        }
        message = header.findViewById(R.id.picker_message)
        path = header.findViewById(R.id.picker_path)
        choose = header.findViewById(R.id.picker_choose)
        progress = header.findViewById(R.id.picker_progress)
        findViewById<View>(R.id.picker_back).setOnClickListener { finish() }
        header.findViewById<View>(R.id.picker_up).setOnClickListener { load(StorageDirectories.parent(current)) }
        header.findViewById<View>(R.id.picker_retry).setOnClickListener { load(current) }
        choose.setOnClickListener { load(current, select = true) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (current == StorageDirectories.ROOT) finish() else load(StorageDirectories.parent(current))
            }
        })
        load(savedInstanceState?.getString(EXTRA_PATH)?.takeIf(StorageDirectories::accepts) ?: StorageDirectories.ROOT)
    }

    private fun load(destination: String, select: Boolean = false) {
        current = destination
        val request = ++generation
        path.text = destination
        choose.isEnabled = false
        entries = emptyList()
        refreshRows()
        list.setSelection(0)
        message.setText(R.string.picker_loading)
        progress.visibility = View.VISIBLE
        header.findViewById<View>(R.id.picker_up).isEnabled = current != StorageDirectories.ROOT
        header.findViewById<View>(R.id.picker_retry).isEnabled = false
        worker.execute {
            val result = runCatching { StorageDirectories.list(destination) }
            runOnUiThread {
                if (isDestroyed || isFinishing || request != generation) return@runOnUiThread
                progress.visibility = View.GONE
                header.findViewById<View>(R.id.picker_retry).isEnabled = true
                if (result.isFailure) {
                    message.setText(R.string.picker_error)
                    return@runOnUiThread
                }
                if (select) {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_PATH, destination))
                    finish()
                    return@runOnUiThread
                }
                entries = result.getOrThrow()
                refreshRows()
                message.setText(if (entries.isEmpty()) R.string.picker_empty else R.string.picker_hint)
                choose.isEnabled = true
            }
        }
    }
    private fun refreshRows() {
        val adapter = list.adapter
        val rows = if (adapter is HeaderViewListAdapter) adapter.wrappedAdapter else adapter
        (rows as BaseAdapter).notifyDataSetChanged()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXTRA_PATH, current)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        generation++
        worker.shutdownNow()
        super.onDestroy()
    }
}
