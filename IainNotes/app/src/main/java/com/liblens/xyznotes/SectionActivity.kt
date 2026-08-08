package com.liblens.xyznotes

import android.content.Intent
import android.graphics.Color
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
import com.liblens.xyznotes.databinding.ActivitySectionBinding
import com.liblens.xyznotes.databinding.DialogSearchBinding
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable

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
            onRename = { note ->
                val input = ActivityBuilder.input(this, note.title)
                ActivityBuilder.dialog(this)
                    .setTitle("Rename Note")
                    .setView(input)
                    .setPositiveButton("Rename") { _, _ ->
                        lifecycleScope.launch {
                            try {
                                val newTitle = input.text.toString().trim()
                                if (newTitle.isNotEmpty()) {
                                    val updated = DataStore.updateNote(
                                        this@SectionActivity, note.copy(title = newTitle)
                                    )
                                    cachedAppData = updated
                                    adapter.updateAlarms(updated.alarms)
                                    adapter.submitNotes(
                                        sortedNotes(updated, updated.notes.filter { it.sectionId == sectionId })
                                    )
                                }
                            } catch (e: Exception) { handleDataStoreError(e) }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { note ->
                AlertDialog.Builder(this, R.style.RoundedDialog)
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
            SearchDialog.show(this, cachedAppData, sectionId)
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
}