package com.example.iainnotes

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivitySectionBinding
import com.example.iainnotes.databinding.DialogSearchBinding
import kotlinx.coroutines.launch

class SectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySectionBinding
    private lateinit var adapter: NoteAdapter
    private var sectionId = ""
    private var sectionName = ""
    private var currentSection: Section? = null
    private var cachedAppData: AppData = AppData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionId = intent.getStringExtra("sectionId") ?: ""
        sectionName = intent.getStringExtra("sectionName") ?: ""
        binding.header.text = "Sections > $sectionName"

        adapter = NoteAdapter(
            onTap = { note ->
                startActivity(
                    Intent(this, NoteDetailActivity::class.java).apply {
                        putExtra("noteId", note.id)
                    }
                )
            },
            onNotifyToggle = { note, enabled ->
                lifecycleScope.launch {
                    try {
                        val updated = note.copy(notifyEnabled = enabled)
                        DataStore.updateNote(this@SectionActivity, updated)
                        if (enabled) NoteNotificationManager.notify(this@SectionActivity, updated)
                        else NoteNotificationManager.cancel(this@SectionActivity, note.id)
                        val data = DataStore.load(this@SectionActivity)
                        cachedAppData = data
                        adapter.updateAlarms(data.alarms)
                        adapter.submitNotes(
                            sortedNotes(data, data.notes.filter { it.sectionId == sectionId })
                        )
                    } catch (e: Exception) { handleDataStoreError(e) }
                }
            },
            onDelete = { note ->
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${note.title}\"?")
                    .setMessage("Any alarm attached to this note will also be deleted.")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val data = DataStore.load(this@SectionActivity)
                                data.alarms.filter { it.noteId == note.id }
                                    .forEach { AlarmScheduler.cancel(this@SectionActivity, it) }
                                NoteNotificationManager.cancel(this@SectionActivity, note.id)
                                val updated = DataStore.deleteNote(this@SectionActivity, note.id)
                                cachedAppData = updated
                                adapter.updateAlarms(updated.alarms)
                                adapter.submitNotes(
                                    sortedNotes(updated, updated.notes.filter { it.sectionId == sectionId })
                                )
                            } catch (e: Exception) { handleDataStoreError(e) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onPin = { note ->
                lifecycleScope.launch {
                    try {
                        val data = DataStore.toggleNotePin(this@SectionActivity, note.id)
                        cachedAppData = data
                        adapter.updateAlarms(data.alarms)
                        adapter.submitNotes(
                            SortHelper.sortedNotes(
                                data.notes.filter { it.sectionId == sectionId },
                                currentSection?.sortOrder ?: "date_created",
                                currentSection?.sortAsc ?: true
                            )
                        )
                    } catch (e: Exception) { handleDataStoreError(e) }
                }
            },
        )

        binding.rvNotes.layoutManager = LinearLayoutManager(this)
        binding.rvNotes.adapter = adapter

        // Sort spinner
        val sortOptions = listOf("Date created", "Alphabetical", "Custom")
        binding.spinnerSort.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            sortOptions
        )

        binding.spinnerSort.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, pos: Int, id: Long
                ) {
                    val order = when (pos) {
                        1 -> "alpha"
                        2 -> "custom"
                        else -> "date_created"
                    }
                    lifecycleScope.launch {
                        try {
                            val section = currentSection ?: return@launch
                            val data = DataStore.updateSectionSort(
                                this@SectionActivity, sectionId, order, section.sortAsc
                            )
                            cachedAppData = data
                            refreshList(data)
                        } catch (e: Exception) { handleDataStoreError(e) }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }

        binding.btnSortDir.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val section = currentSection ?: return@launch
                    val newAsc = !section.sortAsc
                    val data = DataStore.updateSectionSort(
                        this@SectionActivity, sectionId,
                        section.sortOrder, newAsc
                    )
                    cachedAppData = data
                    binding.btnSortDir.setImageResource(
                        if (newAsc) R.drawable.outline_arrow_upward_24
                        else R.drawable.outline_arrow_downward_24
                    )
                    refreshList(data)
                } catch (e: Exception) { handleDataStoreError(e) }
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnSearch.setOnClickListener {
            showSearchDialog()
        }

        binding.fabAddAlarm.setOnClickListener {
            startActivity(
                Intent(this, AddNoteActivity::class.java).apply {
                    putExtra("sectionId", sectionId)
                    putExtra("sectionName", sectionName)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@SectionActivity)
                cachedAppData = data
                refreshList(data)
            } catch (e: Exception) { handleDataStoreError(e) }
        }
    }

    private fun refreshList(data: AppData) {
        currentSection = data.sections.find { it.id == sectionId }
        val section = currentSection ?: return
        adapter.updateAlarms(data.alarms)
        adapter.submitNotes(
            sortedNotes(data, data.notes.filter { it.sectionId == sectionId })
        )
        binding.spinnerSort.setSelection(when (section.sortOrder) {
            "alpha" -> 1
            "custom" -> 2
            else -> 0
        }, false)
        binding.btnSortDir.setImageResource(
            if (section.sortAsc) R.drawable.outline_arrow_upward_24
            else R.drawable.outline_arrow_downward_24
        )
    }

    private fun sortedNotes(data: AppData, notes: List<Note>): List<Note> {
        val section = data.sections.find { it.id == sectionId } ?: return notes
        return SortHelper.sortedNotes(notes, section.sortOrder, section.sortAsc)
    }

    private fun showSearchDialog() {
        val dialogBinding = DialogSearchBinding.inflate(layoutInflater)

        val resultAdapter = SearchResultAdapter { result ->
            startActivity(
                Intent(this, NoteDetailActivity::class.java).apply {
                    putExtra("noteId", result.note.id)
                }
            )
        }
        dialogBinding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvSearchResults.adapter = resultAdapter

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.7f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setGravity(Gravity.TOP)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Push the card below the status bar and any display cutout (e.g. punch-hole camera)
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

        // Scope selection state — true means this section only, false means all
        var scopeThisSectionOnly = true

        fun updateScopeButtons() {
            dialogBinding.btnScopeSection.isSelected = scopeThisSectionOnly
            dialogBinding.btnScopeSection.alpha = if (scopeThisSectionOnly) 1f else 0.5f
            dialogBinding.btnScopeAll.isSelected = !scopeThisSectionOnly
            dialogBinding.btnScopeAll.alpha = if (!scopeThisSectionOnly) 1f else 0.5f
        }

        var optionsVisible = false
        dialogBinding.btnSearchOptions.setOnClickListener {
            optionsVisible = !optionsVisible
            val visibility = if (optionsVisible) View.VISIBLE else View.GONE
            dialogBinding.dividerOptions.visibility = visibility
            dialogBinding.layoutScopeRow.visibility = visibility
            dialogBinding.layoutContentRow.visibility = visibility
        }

        fun runSearch() {
            val query = dialogBinding.etSearchQuery.text.toString()
            val caseSensitive = dialogBinding.btnCaseSensitive.isSelected
            val includeContent = dialogBinding.switchIncludeContent.isChecked
            val scopeId = if (scopeThisSectionOnly) sectionId else null

            val results = SearchHelper.search(
                appData = cachedAppData,
                query = query,
                caseSensitive = caseSensitive,
                scopeSectionId = scopeId,
                includeContent = includeContent
            )
            resultAdapter.submitList(results)
            dialogBinding.tvNoResults.visibility =
                if (results.isEmpty() && query.isNotBlank()) View.VISIBLE else View.GONE
        }

        /*dialogBinding.btnCaseSensitive.setOnClickListener {
            dialogBinding.btnCaseSensitive.isSelected = !dialogBinding.btnCaseSensitive.isSelected
            //dialogBinding.btnCaseSensitive.alpha = if (dialogBinding.btnCaseSensitive.isSelected) 1f else 0.5f
            runSearch()
        }*/
        //dialogBinding.btnCaseSensitive.alpha = 0.5f

        dialogBinding.btnScopeSection.setOnClickListener {
            scopeThisSectionOnly = true
            updateScopeButtons()
            runSearch()
        }
        dialogBinding.btnScopeAll.setOnClickListener {
            scopeThisSectionOnly = false
            updateScopeButtons()
            runSearch()
        }
        updateScopeButtons()

        dialogBinding.switchIncludeContent.setOnCheckedChangeListener { _, _ -> runSearch() }

        dialogBinding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch() }
        })

        dialog.show()
    }
}