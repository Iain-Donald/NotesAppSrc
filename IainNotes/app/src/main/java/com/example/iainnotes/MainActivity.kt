package com.example.iainnotes

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivityMainBinding
//import com.example.iainnotes.Extensions
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SectionAdapter
    private var appData = AppData()
    //private val db by lazy { AppDatabase.getInstance(this) }
    //private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Needed for sections, notes, alarms, and export functionality. Virtually all functionality.
        if (!Environment.isExternalStorageManager()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            AlertDialog.Builder(this)
                .setTitle("Permission needed")
                .setMessage("To fire alarms at exact times, please enable 'Alarms & Reminders' for this app in settings.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:$packageName".toUri()
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Not now", null)
                .show()
        }

        adapter = SectionAdapter(
            onClick = { section ->
                startActivity(
                    Intent(this, SectionActivity::class.java).apply {
                        putExtra("sectionId", section.id)
                        putExtra("sectionName", section.name)
                    }
                )
            },
            onRename = { section ->
                val input = EditText(this).apply {
                    setText(section.name)
                    hint = "Section name"
                    setPadding(48, 24, 48, 24)
                }
                AlertDialog.Builder(this)
                    .setTitle("Rename Section")
                    .setView(input)
                    .setPositiveButton("Rename") { _, _ ->
                        lifecycleScope.launch {
                            val newName = input.text.toString().trim()
                            if (newName.isNotEmpty()) {
                                appData = DataStore.renameSection(this@MainActivity, section.id, newName)
                                adapter.submitList(appData.sections.toList())
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { section ->
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${section.name}\"?")
                    .setMessage("All alarms in this section will also be deleted.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            appData = DataStore.deleteSection(this@MainActivity, section.id)
                            adapter.submitList(appData.sections.toList())
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnLock.setOnClickListener {
            showLockDialog()
        }

        binding.rvSections.layoutManager = LinearLayoutManager(this)
        binding.rvSections.adapter = adapter

        binding.fabAddSection.setOnClickListener { showAddSectionDialog() }
    }

    override fun onResume() {
        super.onResume()
        val prefs = PreferencesManager.load()
        binding.btnLock.visibility =
            if (prefs.usePassphrase) View.VISIBLE else View.GONE
        lifecycleScope.launch {
            try {
                appData = DataStore.load(this@MainActivity)
                adapter.submitList(appData.sections.toList())
            } catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = PreferencesManager.load()
        if (prefs.lockOnClose) DataStore.lock()
    }

    private fun showLockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lock, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val tvCountdown = dialogView.findViewById<TextView>(R.id.tvCountdown)
        val btnLeave = dialogView.findViewById<Button>(R.id.btnLeave)

        var secondsLeft = 3
        val timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft--
                tvCountdown.text = "Closing in $secondsLeft..."
            }
            override fun onFinish() {
                DataStore.lock()
                dialog.dismiss()
                finishAffinity()
            }
        }
        timer.start()

        btnLeave.setOnClickListener {
            timer.cancel()
            DataStore.lock()
            dialog.dismiss()
            finishAffinity()
        }

        dialog.show()
    }

    private fun showAddSectionDialog() {
        val input = EditText(this).apply {
            hint = "Section name"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("New Section")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                lifecycleScope.launch {
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        appData = DataStore.addSection(this@MainActivity, name)
                        adapter.submitList(appData.sections.toList())
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * A native method that is implemented by the 'iainnotes' native library,
     * which is packaged with this application.
     */
    //external fun stringFromJNI(): String

    /*companion object {
        // Used to load the 'iainnotes' library on application startup.
        init {
            System.loadLibrary("iainnotes")
        }
    }*/
}