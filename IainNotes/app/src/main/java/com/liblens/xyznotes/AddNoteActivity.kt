package com.liblens.xyznotes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liblens.xyznotes.databinding.ActivityAddNoteBinding

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
            binding.btnSaveNote.isEnabled = false              // ← add
            lifecycleScope.launch {
                try {
                    val data = DataStore.load(this@AddNoteActivity)
                    existingNote = data.notes.find { it.id == editNoteId }
                    existingNote?.let { note ->
                        binding.header.text = "<edit/${note.title}>"
                        binding.etNoteTitle.setText(note.title)
                        binding.etNoteContent.setText(note.content)
                    }
                } catch (e: Exception) {
                    handleDataStoreError(e)
                } finally {
                    binding.btnSaveNote.isEnabled = true       // ← add
                }
            }
        } else {
            binding.header.text = "<$sectionName/new note>"
        }

        binding.btnSaveNote.setOnClickListener { saveNote() }
    }

    private fun saveNote() {
        var title = binding.etNoteTitle.text.toString().trim()
        if (title.isEmpty()) title = "Title"
        val content = binding.etNoteContent.text.toString()
        val editNoteId = intent.getStringExtra("editNoteId")

        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@AddNoteActivity)
                // Re-resolve from freshly loaded data — never trust a field written by
                // the onCreate coroutine, which may belong to a discarded instance.
                val existing = editNoteId?.let { id -> data.notes.find { it.id == id } }

                if (editNoteId != null && existing == null) {
                    Toast.makeText(
                        this@AddNoteActivity,
                        "Could not find note to edit", Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val note = Note(
                    id = existing?.id ?: generateId("n"),
                    sectionId = existing?.sectionId ?: sectionId,
                    title = title,
                    content = content,
                    createdAt = existing?.createdAt ?: currentTimestamp(),
                    notifyEnabled = existing?.notifyEnabled ?: false,
                    pinned = existing?.pinned ?: false
                )
                if (existing != null) DataStore.updateNote(this@AddNoteActivity, note)
                else DataStore.addNote(this@AddNoteActivity, note)
                finish()
            } catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }
}