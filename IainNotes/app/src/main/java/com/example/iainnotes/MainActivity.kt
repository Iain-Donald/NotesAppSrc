package com.example.iainnotes

import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SectionAdapter
    private var appData = AppData()
    private var currentSortOrder = "date_created"
    private var currentSortAsc = true
    private var spinnerReady = false

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

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1002
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
            },
            onMove = { _, _ -> }
        )

        val sortOptions = listOf("Date created", "Alphabetical")
        binding.spinnerSortSections.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sortOptions
        )

        binding.spinnerSortSections.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, pos: Int, id: Long
                ) {
                    if (!spinnerReady) return
                    currentSortOrder = when (pos) {
                        1 -> "alpha"
                        2 -> "custom"
                        else -> "date_created"
                    }
                    lifecycleScope.launch {
                        try {
                            appData = DataStore.updateAppSectionSort(
                                this@MainActivity, currentSortOrder, currentSortAsc
                            )
                            adapter.submitList(sortedSections())
                        } catch (e: Exception) { handleDataStoreError(e) }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        binding.btnSortDirSections.setOnClickListener {
            currentSortAsc = !currentSortAsc
            binding.btnSortDirSections.setImageResource(
                if (currentSortAsc) R.drawable.outline_arrow_upward_24
                else R.drawable.outline_arrow_downward_24
            )
            lifecycleScope.launch {
                try {
                    appData = DataStore.updateAppSectionSort(
                        this@MainActivity, currentSortOrder, currentSortAsc
                    )
                    adapter.submitList(sortedSections())
                } catch (e: Exception) { handleDataStoreError(e) }
            }
        }

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
        NoteNotificationManager.createChannel(this)

        if (intent.getBooleanExtra("lock_and_close", false)) {
            finish()
            return
        }

        val prefs = PreferencesManager.load()
        binding.btnLock.visibility =
            if (prefs.usePassphrase) View.VISIBLE else View.GONE

        if (prefs.usePassphrase && DataStore.isUnlocked()) {
            LockNotificationService.start(this)
        }

        lifecycleScope.launch {
            try {
                appData = DataStore.load(this@MainActivity)
                NoteNotificationManager.syncAll(this@MainActivity, appData.notes)

                // Sort UI sync now inside coroutine where appData is fresh
                currentSortOrder = appData.sectionSortOrder
                currentSortAsc = appData.sectionSortAsc
                spinnerReady = false
                binding.spinnerSortSections.setSelection(when (currentSortOrder) {
                    "alpha" -> 1
                    "custom" -> 2
                    else -> 0
                }, false)
                binding.btnSortDirSections.setImageResource(
                    if (currentSortAsc) R.drawable.outline_arrow_upward_24
                    else R.drawable.outline_arrow_downward_24
                )
                spinnerReady = true
                adapter.submitList(sortedSections())
            } catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val prefs = PreferencesManager.load()
        if (prefs.lockOnClose) {
            DataStore.lock()
            LockNotificationService.stop(this)
        }
    }

    private fun sortedSections(): List<Section> {
        return SortHelper.sortedSections(
            appData.sections,
            appData.sectionSortOrder,
            appData.sectionSortAsc
        )
    }

    private fun showLockDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lock, null)
        val dialog = AlertDialog.Builder(this@MainActivity)
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
                LockNotificationService.stop(this@MainActivity)
                dialog.dismiss()
                finishAffinity()
            }
        }
        timer.start()

        btnLeave.setOnClickListener {
            timer.cancel()
            DataStore.lock()
            LockNotificationService.stop(this@MainActivity)
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

}