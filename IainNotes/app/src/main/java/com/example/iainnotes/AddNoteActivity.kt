package com.example.iainnotes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iainnotes.databinding.ActivityAddNoteBinding

//import com.example.iainnotes.IdGenerator.makeId
import kotlinx.coroutines.launch

class AddNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddNoteBinding
    private var sectionId = ""
    private var existingNote: Note? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityAddNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sectionId = intent.getStringExtra("sectionId") ?: ""
        val sectionName = intent.getStringExtra("sectionName") ?: ""
        val editNoteId = intent.getStringExtra("editNoteId")

        if (editNoteId != null) {
            lifecycleScope.launch {
                try {
                    val data = DataStore.load(this@AddNoteActivity)
                    existingNote = data.notes.find { it.id == editNoteId }
                    existingNote?.let { note ->
                        binding.header.text = "<edit/${note.title}>"
                        binding.etNoteTitle.setText(note.title)
                        binding.etNoteContent.setText(note.content)
                    }
                }  catch (e: Exception) {
                    handleDataStoreError(e)
                }
            }
        } else {
            binding.header.text = "<$sectionName/new note>"
        }

        binding.btnSaveNote.setOnClickListener { saveNote() }
    }

    private fun saveNote() {
        val title = binding.etNoteTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show()
            return
        }
        val note = Note(
            id = existingNote?.id ?: generateId("n"),
            sectionId = sectionId,
            title = title,
            content = binding.etNoteContent.text.toString()
        )
        lifecycleScope.launch {
            if (existingNote != null) {
                DataStore.updateNote(this@AddNoteActivity, note)
            } else {
                DataStore.addNote(this@AddNoteActivity, note)
            }
            finish()
        }
    }
}