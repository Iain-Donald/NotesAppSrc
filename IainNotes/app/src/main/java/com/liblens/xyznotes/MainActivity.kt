package com.liblens.xyznotes

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liblens.xyznotes.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SectionAdapter
    private var appData = AppData()
    private var currentSortOrder = "date_created"
    private var currentSortAsc = true
    private var spinnerReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.i("XYNC", "bg=" +
                Integer.toHexString(androidx.core.content.ContextCompat.getColor(this, R.color.appBackground)) +
                " surface=" + Integer.toHexString(androidx.core.content.ContextCompat.getColor(this, R.color.appSurface)) +
                " mode=" + androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() +
                " uiMode=" + (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK))

        // Needed for sections, notes, alarms, and export functionality. Virtually all functionality.
        /*if (!Environment.isExternalStorageManager()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                }
            )
        }*/

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1002
            )
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
                val view = ActivityBuilder.sectionEditor(
                    this,
                    section.name,
                    section.categoryId,
                    appData.categories,
                    onCreateCategory = { done ->
                        ActivityBuilder.newCategoryDialog(this) { name, colorId ->
                            lifecycleScope.launch {
                                appData = DataStore.addCategory(this@MainActivity, name, colorId)
                                appData.categories.lastOrNull()?.let { done(it) }
                            }
                        }
                    },
                    onEditCategory = { cat, done ->
                        ActivityBuilder.categoryDialog(
                            this,
                            existing = cat,
                            onSubmit = { name, colorId ->
                                lifecycleScope.launch {
                                    appData = DataStore.updateCategory(this@MainActivity, cat.id, name, colorId)
                                    done(appData.categories.find { it.id == cat.id })
                                    refresh()
                                }
                            },
                            onDelete = {
                                lifecycleScope.launch {
                                    appData = DataStore.deleteCategory(this@MainActivity, cat.id)
                                    done(null)
                                    refresh()
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Group \"${cat.name}\" deleted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                )
                val input = view.findViewById<EditText>(R.id.sectionInput)

                ActivityBuilder.dialog(this)
                    .setTitle("Rename Section")
                    .setView(view)
                    .setPositiveButton("Rename") { _, _ ->
                        lifecycleScope.launch {
                            val newName = input.text.toString().trim()
                            val newCat = ActivityBuilder.selectedCategoryId(view)
                            if (newName.isNotEmpty() && newName != section.name) {
                                appData = DataStore.renameSection(this@MainActivity, section.id, newName)
                            }
                            if (newCat != section.categoryId) {
                                appData = DataStore.setSectionCategory(this@MainActivity, section.id, newCat)
                            }
                            refresh()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { section ->
                AlertDialog.Builder(this, R.style.RoundedDialog)
                    .setTitle("Delete \"${section.name}\"?")
                    .setMessage("All alarms in this section will also be deleted.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val data = DataStore.load(this@MainActivity)
                                val doomedNotes = data.notes.filter { it.sectionId == section.id }
                                data.alarms
                                    .filter { a -> doomedNotes.any { it.id == a.noteId } }
                                    .forEach { AlarmScheduler.cancel(this@MainActivity, it) }
                                doomedNotes.forEach {
                                    NoteNotificationManager.cancel(this@MainActivity, it.id)
                                }
                                appData = DataStore.deleteSection(this@MainActivity, section.id)
                                refresh()
                            } catch (e: Exception) { handleDataStoreError(e) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onPin = { section ->
                lifecycleScope.launch {
                    try {
                        appData = DataStore.toggleSectionPin(this@MainActivity, section.id)
                        refresh()
                    } catch (e: Exception) { handleDataStoreError(e) }
                }
            }
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
                            refresh()
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
                    refresh()
                } catch (e: Exception) { handleDataStoreError(e) }
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            SearchDialog.show(this, appData)
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
        binding.root.post {
            val fromRes = ContextCompat.getColor(this, R.color.appBackground)
            val fromWin = (window.decorView.background as? android.graphics.drawable.ColorDrawable)?.color
            android.util.Log.i("XYNC", "RESUME res=${Integer.toHexString(fromRes)}" +
                    " window=${fromWin?.let { Integer.toHexString(it) }}" +
                    " mode=${AppCompatDelegate.getDefaultNightMode()}" +
                    " uiMode=${resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK}")
        }
        NoteNotificationManager.createChannel(this)

        var prefs = PreferencesManager.load()

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED && !prefs.notifPromptShown) {
            prefs = prefs.copy(notifPromptShown = true)
            PreferencesManager.save(prefs)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
        }

        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted && !prefs.dndPromptDismissed) {
            ActivityBuilder.dialog(this)
                .setTitle("Allow reminders during Do Not Disturb?")
                .setMessage("Note reminders will be silenced by Do Not Disturb unless you grant this.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton("Not now") { _, _ ->
                    // Asked once. The setting stays reachable in Settings.
                    PreferencesManager.save(
                        PreferencesManager.load().copy(dndPromptDismissed = true)
                    )
                }
                .show()
        }
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
                    /*"custom" -> 2*/
                    else -> 0
                }, false)
                binding.btnSortDirSections.setImageResource(
                    if (currentSortAsc) R.drawable.outline_arrow_upward_24
                    else R.drawable.outline_arrow_downward_24
                )
                spinnerReady = true
                refresh()
            } catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // isFinishing excludes configuration changes and Activity recreation —
        // a theme switch or rotation must not be treated as closing the app.
        if (!isFinishing || isChangingConfigurations) return
        if (PreferencesManager.load().lockOnClose) {
            DataStore.lock()
            LockNotificationService.stop(this)
        }
    }

    private fun refresh() {
        adapter.updateCategories(appData.categories)
        adapter.submitSections(sortedSections())
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
        val dialog = ActivityBuilder.dialog(this@MainActivity)
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
        val input = ActivityBuilder.input(this)
        AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle("New Section")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                lifecycleScope.launch {
                    var name = input.text.toString().trim()
                    if (name.isEmpty()) name = "Section"
                    appData = DataStore.addSection(this@MainActivity, name)
                    refresh()
                    Toast.makeText(this@MainActivity, "Section \"$name\" added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}