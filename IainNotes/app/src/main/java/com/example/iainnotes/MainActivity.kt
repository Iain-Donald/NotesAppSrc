package com.example.iainnotes

import android.app.Dialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivityMainBinding
import com.example.iainnotes.databinding.DialogSearchBinding
//import com.example.iainnotes.databinding.ItemDialogalertBinding
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable

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
                                    adapter.updateCategories(appData.categories)
                                    adapter.submitList(sortedSections())
                                }
                            },
                            onDelete = {
                                lifecycleScope.launch {
                                    appData = DataStore.deleteCategory(this@MainActivity, cat.id)
                                    done(null)
                                    adapter.updateCategories(appData.categories)
                                    adapter.submitList(sortedSections())
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
                            adapter.updateCategories(appData.categories)
                            adapter.submitList(sortedSections())
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
                                adapter.updateCategories(appData.categories)
                                adapter.submitList(sortedSections())
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
                        adapter.updateCategories(appData.categories)
                        adapter.submitList(sortedSections())
                    } catch (e: Exception) { handleDataStoreError(e) }
                }
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
                            adapter.updateCategories(appData.categories)
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
                    adapter.updateCategories(appData.categories)
                    adapter.submitList(sortedSections())
                } catch (e: Exception) { handleDataStoreError(e) }
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            showSearchDialog()
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

        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            ActivityBuilder.dialog(this)
                .setTitle("Allow reminders during Do Not Disturb?")
                .setMessage("Note reminders will be silenced by Do Not Disturb unless you grant this.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                .setNegativeButton("Not now", null)
                .show()
        }

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
                adapter.updateCategories(appData.categories)
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

    /*private fun showAddSectionDialog() {
        val ctx = ContextThemeWrapper(this, R.style.RoundedDialog)
        val input = EditText(ctx).apply {
            hint = "Section name"
            setPadding(48, 24, 48, 24)
        }
        AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle("New Section")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                lifecycleScope.launch {
                    var name = input.text.toString().trim()
                    if (name.isEmpty()) name = "Section"
                    appData = DataStore.addSection(this@MainActivity, name)
                    adapter.submitList(sortedSections())
                    Toast.makeText(this@MainActivity, "Section \"$name\" added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }*/
    /*private fun showAddSectionDialog() {
        val ctx = ContextThemeWrapper(this, R.style.RoundedDialog)
        val binding = ItemDialogalertBinding.inflate(layoutInflater.cloneInContext(ctx))

        AlertDialog.Builder(this, R.style.RoundedDialog)
            .setTitle("New Section")
            .setView(binding.root)
            .setPositiveButton("Add") { _, _ ->
                lifecycleScope.launch {
                    var name = binding.sectionInput.text.toString().trim()
                    if (name.isEmpty()) name = "Section"
                    appData = DataStore.addSection(this@MainActivity, name)
                    adapter.submitList(sortedSections())
                    Toast.makeText(this@MainActivity, "Section \"$name\" added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }*/

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
                    adapter.updateCategories(appData.categories)
                    adapter.submitList(sortedSections())
                    Toast.makeText(this@MainActivity, "Section \"$name\" added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearchDialog() {
        val dialogBinding = DialogSearchBinding.inflate(layoutInflater)

        // Scope row is never shown at top level — always hidden
        dialogBinding.layoutScopeRow.visibility = View.GONE

        val resultAdapter = SearchResultAdapter { result ->
            startActivity(
                Intent(this, NoteDetailActivity::class.java).apply {
                    putExtra("noteId", result.note.id)
                }
            )
        }
        dialogBinding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvSearchResults.adapter = resultAdapter

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setDimAmount(0.7f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.TOP)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Push the card below the status bar and any display cutout (e.g. punch-hole camera)
        /*dialogBinding.cardSearch.post {
            val insets = dialogBinding.cardSearch.rootWindowInsets
            val topInset = if (insets != null) {
                val cutout = insets.displayCutout?.safeInsetTop ?: 0
                maxOf(insets.systemWindowInsetTop, cutout)
            } else 0
            val params = dialogBinding.cardSearch.layoutParams as android.widget.FrameLayout.LayoutParams
            params.topMargin = topInset + 8
            dialogBinding.cardSearch.layoutParams = params
        }*/
        dialogBinding.cardSearch.post {
            val insets = ViewCompat.getRootWindowInsets(dialogBinding.cardSearch)
            val topInset = if (insets != null) {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
                maxOf(systemBars, cutout)
            } else { 0 }
            val params = dialogBinding.cardSearch.layoutParams as android.widget.FrameLayout.LayoutParams
            params.topMargin = topInset + 8
            dialogBinding.cardSearch.layoutParams = params
        }

        var optionsVisible = false
        dialogBinding.btnSearchOptions.setOnClickListener {
            optionsVisible = !optionsVisible
            val visibility = if (optionsVisible) View.VISIBLE else View.GONE
            dialogBinding.dividerOptions.visibility = visibility
            dialogBinding.layoutContentRow.visibility = visibility
        }

        fun runSearch() {
            val query = dialogBinding.etSearchQuery.text.toString()
            val caseSensitive = dialogBinding.btnCaseSensitive.isSelected
            val includeContent = dialogBinding.switchIncludeContent.isChecked

            val results = SearchHelper.search(
                appData = appData,
                query = query,
                caseSensitive = caseSensitive,
                scopeSectionId = null,
                includeContent = includeContent
            )
            resultAdapter.submitList(results)
            dialogBinding.tvNoResults.visibility =
                if (results.isEmpty() && query.isNotBlank()) View.VISIBLE else View.GONE
        }

        dialogBinding.btnCaseSensitive.setOnClickListener {
            dialogBinding.btnCaseSensitive.isSelected = !dialogBinding.btnCaseSensitive.isSelected
            //dialogBinding.btnCaseSensitive.alpha = if (dialogBinding.btnCaseSensitive.isSelected) 1f else 0.5f
            runSearch()
        }
        //dialogBinding.btnCaseSensitive.alpha = 0.5f

        dialogBinding.switchIncludeContent.setOnCheckedChangeListener { _, _ -> runSearch() }

        dialogBinding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch() }
        })

        dialog.show()
    }

}