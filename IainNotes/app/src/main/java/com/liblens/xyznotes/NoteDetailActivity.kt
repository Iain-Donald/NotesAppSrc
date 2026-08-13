package com.liblens.xyznotes
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liblens.xyznotes.databinding.ActivityNoteDetailBinding
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
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySkin()
        android.util.Log.i("XYNC", "detail skin applied, textPrimary=${Integer.toHexString(Palette.textPrimary)}")

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
                ActivityBuilder.dialog(this)
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
        applySkin()
        loadNote()
    }

    private fun applySkin() {
        binding.root.setBackgroundColor(Palette.background)
        binding.header.setTextColor(Palette.textPrimary)
        binding.divider.setBackgroundColor(Palette.divider)

        binding.etNoteContent.setTextColor(Palette.textBody)
        binding.etNoteContent.setHintTextColor(Palette.textHint)

        binding.btnEditNote.imageTintList = Palette.tint(Palette.icon)
        // btnNotify is state-dependent; loadNote() sets its tint.

        binding.btnAddAlarm.backgroundTintList = Palette.tint(Palette.button)
        binding.btnAddAlarm.setTextColor(Palette.buttonText)

        binding.etNoteContent.setBackgroundColor(Palette.input)

        listOf(binding.btnToggleAlarms, binding.btnSaveNote).forEach {
            it.backgroundTintList = Palette.tint(Palette.button)
            it.imageTintList = Palette.tint(Palette.buttonText)
        }
        if(::alarmAdapter.isInitialized) alarmAdapter.notifyDataSetChanged()
    }

    private fun loadNote() {
        lifecycleScope.launch {
            val data = DataStore.load(this@NoteDetailActivity)
            val note = data.notes.find { it.id == noteId } ?: return@launch
            val alarms = data.alarms.filter { it.noteId == noteId }

            binding.header.text = run {
                val sectionName = data.sections.find { it.id == note.sectionId }?.name
                if (sectionName != null) "Sections > $sectionName > ${note.title}"
                else "Sections > ${note.title}"
            }

            if (!noteLoaded) {
                // First load — populate the editor and set the baseline for diff tracking.
                binding.etNoteContent.setText(note.content)
                savedContent = note.content.toCharArray()
                noteLoaded = true
            }
            // On subsequent resumes (e.g. returning from app switcher or AddAlarmActivity),
            // leave the editor content and savedContent alone so unsaved edits are preserved.

            alarmAdapter.submitList(alarms)
            if (alarms.any { it.isActive } && !AlarmScheduler.canScheduleExact(this@NoteDetailActivity)) {
                Toast.makeText(
                    this@NoteDetailActivity,
                    "Exact reminders are off — alarms may be delayed",
                    Toast.LENGTH_SHORT
                ).show()
            }
            updateSaveButton()

            // Update icon based on current state
            binding.btnNotify.imageTintList = Palette.tint(
                if (note.notifyEnabled) Palette.accent else Palette.iconDim
            )

            // Re-set listener with fresh note reference each load
            binding.btnNotify.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val updated = note.copy(notifyEnabled = !note.notifyEnabled)
                        //DataStore.updateNote(this@NoteDetailActivity, updated)
                        DataStore.setNoteNotify(this@NoteDetailActivity, noteId, updated.notifyEnabled)
                        val check = DataStore.load(this@NoteDetailActivity)
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
            binding.btnSaveNote.backgroundTintList = Palette.tint(Palette.button)
            binding.btnSaveNote.imageTintList = Palette.tint(Palette.buttonText)
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