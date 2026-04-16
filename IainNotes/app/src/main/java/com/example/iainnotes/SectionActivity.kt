package com.example.iainnotes

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
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
                                val updated = DataStore.deleteNote(this@SectionActivity, note.id)
                                adapter.submitList(updated.notes.filter { it.sectionId == sectionId })
                            }  catch (e: Exception) {
                                handleDataStoreError(e)
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvNotes.layoutManager = LinearLayoutManager(this)
        binding.rvNotes.adapter = adapter

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
                adapter.updateAlarms(data.alarms)
                adapter.submitList(data.notes.filter { it.sectionId == sectionId })
            }  catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }
}