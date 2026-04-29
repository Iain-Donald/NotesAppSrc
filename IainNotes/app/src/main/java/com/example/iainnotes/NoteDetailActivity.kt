package com.example.iainnotes
// TODO: test
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iainnotes.databinding.ActivityNoteDetailBinding
import kotlinx.coroutines.launch
//import androidx.core.graphics.toColorInt

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var alarmAdapter: AlarmAdapter
    private var noteId = ""
    private var savedContent: CharArray = charArrayOf()
    private var noteLoaded = false
    private val diff get() = binding.etNoteContent.text.toString() != String(savedContent)

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Alarm panel starts hidden
        var alarmPanelVisible = false

        binding.btnToggleAlarms.setOnClickListener {
            alarmPanelVisible = !alarmPanelVisible
            animate_Alarm(alarmPanelVisible)
        }

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

        // Temporary test
        binding.btnNotify.setOnClickListener {
            android.util.Log.d("NOTIFY", "btnNotify tapped in onCreate")
            Toast.makeText(this, "notify tapped", Toast.LENGTH_SHORT).show()
        }

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
        noteLoaded = false
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
            val data = DataStore.load(this@NoteDetailActivity)
            val note = data.notes.find { it.id == noteId } ?: return@launch
            val alarms = data.alarms.filter { it.noteId == noteId }

            binding.header.text = "<${note.title}>"

            if (!noteLoaded) {
                // First load — populate the editor and set the baseline for diff tracking.
                binding.etNoteContent.setText(note.content)
                savedContent = note.content.toCharArray()
                noteLoaded = true
            }
            // On subsequent resumes (e.g. returning from app switcher or AddAlarmActivity),
            // leave the editor content and savedContent alone so unsaved edits are preserved.

            alarmAdapter.submitList(alarms)
            updateSaveButton()

            // Update icon based on current state
            binding.btnNotify.setImageResource(
                if (note.notifyEnabled) R.drawable.baseline_notifications_24
                else R.drawable.outline_notifications_off_24
            )

            // Re-set listener with fresh note reference each load
            binding.btnNotify.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val updated = note.copy(notifyEnabled = !note.notifyEnabled)
                        android.util.Log.d("NOTIFY", "note.notifyEnabled before: ${note.notifyEnabled}")
                        android.util.Log.d("NOTIFY", "updated.notifyEnabled: ${updated.notifyEnabled}")
                        DataStore.updateNote(this@NoteDetailActivity, updated)
                        val check = DataStore.load(this@NoteDetailActivity)
                        android.util.Log.d("NOTIFY", "after load notifyEnabled: ${check.notes.find { it.id == noteId }?.notifyEnabled}")
                        if (updated.notifyEnabled) {
                            NoteNotificationManager.notify(this@NoteDetailActivity, updated)
                        } else {
                            NoteNotificationManager.cancel(this@NoteDetailActivity, note.id)
                        }
                        loadNote()
                    } catch (e: Exception) {
                        handleDataStoreError(e)
                    }
                }
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
        if (hasChanges) {
            binding.btnSaveNote.backgroundTintList = ColorStateList.valueOf(
                getColor(com.google.android.material.R.color.design_default_color_primary))
            binding.btnSaveNote.visibility = View.VISIBLE
        } else {
            binding.btnSaveNote.visibility = View.GONE
        }
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

    private fun animate_Alarm(alarmVisible: Boolean) {
        if (alarmVisible) {
            // Slide down — animate from 0 height to wrap_content
            binding.layoutAlarmSection.visibility = View.VISIBLE
            binding.layoutAlarmSection.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = binding.layoutAlarmSection.measuredHeight
            binding.layoutAlarmSection.layoutParams.height = 0
            binding.layoutAlarmSection.requestLayout()

            ValueAnimator.ofInt(0, targetHeight).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    binding.layoutAlarmSection.layoutParams.height =
                        animator.animatedValue as Int
                    binding.layoutAlarmSection.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // Let it wrap content naturally after animation
                        binding.layoutAlarmSection.layoutParams.height =
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        binding.layoutAlarmSection.requestLayout()
                    }
                })
                start()
            }
        } else {
            // Slide up — animate from current height to 0
            val initialHeight = binding.layoutAlarmSection.measuredHeight
            ValueAnimator.ofInt(initialHeight, 0).apply {
                duration = 300
                interpolator = AccelerateInterpolator()
                addUpdateListener { animator ->
                    binding.layoutAlarmSection.layoutParams.height =
                        animator.animatedValue as Int
                    binding.layoutAlarmSection.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.layoutAlarmSection.visibility = View.GONE
                        binding.layoutAlarmSection.layoutParams.height =
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                })
                start()
            }
        }
    }
}