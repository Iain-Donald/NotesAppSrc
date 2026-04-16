package com.example.iainnotes

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivityNoteDetailBinding
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var alarmAdapter: AlarmAdapter
    private var noteId = ""
    private var savedContent: CharArray = charArrayOf()
    private val diff get() = binding.etNoteContent.text.toString() != String(savedContent)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (diff) {
                    showUnsavedChangesDialog()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        noteId = intent.getStringExtra("noteId") ?: return

        alarmAdapter = AlarmAdapter(
            onToggle = { alarm, checked ->
                lifecycleScope.launch {
                    val updated = alarm.copy(isActive = checked)
                    DataStore.updateAlarm(this@NoteDetailActivity, updated)
                    if (checked) AlarmScheduler.schedule(this@NoteDetailActivity, updated)
                    else AlarmScheduler.cancel(this@NoteDetailActivity, updated)
                }
            },
            onEdit = { alarm ->
                startActivity(
                    Intent(this, AddAlarmActivity::class.java).apply {
                        putExtra("editAlarmId", alarm.id)
                    }
                )
            },
            onDelete = { alarm ->
                AlertDialog.Builder(this)
                    .setTitle("Remove \"${alarm.name}\"?")
                    .setPositiveButton("Remove") { _, _ ->
                        lifecycleScope.launch {
                            AlarmScheduler.cancel(this@NoteDetailActivity, alarm)
                            DataStore.deleteAlarm(this@NoteDetailActivity, alarm.id)
                            loadNote()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvAlarms.layoutManager = LinearLayoutManager(this)
        binding.rvAlarms.adapter = alarmAdapter

        binding.btnAddAlarm.setOnClickListener {
            startActivity(
                Intent(this, AddAlarmActivity::class.java).apply {
                    putExtra("noteId", noteId)
                }
            )
        }

        binding.btnEditNote.setOnClickListener {
            startActivity(
                Intent(this, AddNoteActivity::class.java).apply {
                    putExtra("editNoteId", noteId)
                }
            )
        }

        binding.btnSaveNote.setOnClickListener {
            saveNote()
        }

        // Watch for text changes to update save button color
        binding.etNoteContent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSaveButton()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        savedContent.fill('\u0000')
        savedContent = charArrayOf()
        binding.etNoteContent.text?.clear()
    }

    override fun onResume() {
        super.onResume()
        loadNote()
    }

    private fun loadNote() {
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@NoteDetailActivity)
                val note = data.notes.find { it.id == noteId } ?: return@launch
                val alarms = data.alarms.filter { it.noteId == noteId }

                binding.header.text = "<${note.title}>"

                // Update savedContent and field together on load
                val contentChars = note.content.toCharArray()
                if (!binding.etNoteContent.text.toString().toCharArray()
                        .contentEquals(contentChars)
                ) {
                    binding.etNoteContent.setText(note.content)
                }
                savedContent = contentChars

                alarmAdapter.submitList(alarms)
                updateSaveButton()
            }  catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }

    private fun saveNote() {
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@NoteDetailActivity)
                val note = data.notes.find { it.id == noteId } ?: return@launch
                val updated = note.copy(content = binding.etNoteContent.text.toString())
                DataStore.updateNote(this@NoteDetailActivity, updated)
                savedContent = updated.content.toCharArray()
                updateSaveButton()
                Toast.makeText(this@NoteDetailActivity, "Note saved", Toast.LENGTH_SHORT).show()
            }  catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }

    private fun updateSaveButton() {
        val hasChanges = diff
        binding.btnSaveNote.isEnabled = hasChanges
        binding.btnSaveNote.backgroundTintList = ColorStateList.valueOf(
            if (hasChanges)
                getColor(com.google.android.material.R.color.design_default_color_primary)
            else
                android.graphics.Color.parseColor("#AAAAAA")
        )
    }

    private fun showUnsavedChangesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_unsaved_changes, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnSaveAndLeave).setOnClickListener {
            saveNote()
            dialog.dismiss()
            finish()
        }
        dialogView.findViewById<Button>(R.id.btnDiscardAndLeave).setOnClickListener {
            dialog.dismiss()
            finish()
        }
        dialogView.findViewById<Button>(R.id.btnCancelDialog).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}