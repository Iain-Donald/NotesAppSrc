package com.example.iainnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivitySectionBinding
import kotlinx.coroutines.launch

//jsonwS
class SectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySectionBinding
    private lateinit var adapter: NoteAdapter
    private var sectionId = ""
    private var sectionName = ""
    private var currentSection: Section? = null
    private var pendingCustomOrder: List<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionId = intent.getStringExtra("sectionId") ?: ""
        sectionName = intent.getStringExtra("sectionName") ?: ""
        binding.header.text = "<sections/$sectionName>"

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
                        DataStore.toggleNotePin(this@SectionActivity, note.id)
                        val data = DataStore.load(this@SectionActivity)
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
                    binding.btnSortDir.setImageResource(
                        if (newAsc) R.drawable.outline_arrow_upward_24
                        else R.drawable.outline_arrow_downward_24
                    )
                    refreshList(data)
                } catch (e: Exception) { handleDataStoreError(e) }
            }
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
        // Save pending custom order if user dragged then navigated away
        /*pendingCustomOrder?.let { order ->
            lifecycleScope.launch {
                try {
                    DataStore.updateSectionCustomOrder(this@SectionActivity, sectionId, order)
                    pendingCustomOrder = null
                } catch (e: Exception) { handleDataStoreError(e) }
            }
        }*/
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@SectionActivity)
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
        // Sync spinner to current sort
        binding.spinnerSort.setSelection(when (section.sortOrder) {
            "alpha" -> 1
            "custom" -> 2
            else -> 0
        }, false)  // false = no listener trigger
        binding.btnSortDir.setImageResource(
            if (section.sortAsc) R.drawable.outline_arrow_upward_24
            else R.drawable.outline_arrow_downward_24
        )
    }

    private fun sortedNotes(data: AppData, notes: List<Note>): List<Note> {
        val section = data.sections.find { it.id == sectionId } ?: return notes
        return SortHelper.sortedNotes(notes, section.sortOrder, section.sortAsc/*, section.customOrder*/)
    }
}