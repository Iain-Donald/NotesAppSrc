package com.liblens.xyznotes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liblens.xyznotes.databinding.ActivityAddAlarmBinding
import kotlinx.coroutines.launch

class AddAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddAlarmBinding
    private var noteId = ""
    private var existingAlarm: Alarm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityAddAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getStringExtra("noteId") ?: ""
        val editAlarmId = intent.getStringExtra("editAlarmId")

        if (editAlarmId != null) {
            binding.btnSaveAlarm.isEnabled = false
            lifecycleScope.launch {
                try {
                    val appData = DataStore.load(this@AddAlarmActivity)
                    existingAlarm = appData.alarms.find { it.id == editAlarmId }
                    existingAlarm?.let { alarm ->
                        binding.header.text = "<edit/${alarm.name}>"
                        binding.etName.setText(alarm.name)
                        binding.timePicker.hour = alarm.timeHour
                        binding.timePicker.minute = alarm.timeMinute
                        binding.etDisplayText.setText(alarm.displayText)
                        //binding.switchActive.isChecked = alarm.isActive
                        binding.btnMon.isChecked = "MON" in alarm.repeatDays
                        binding.btnTue.isChecked = "TUE" in alarm.repeatDays
                        binding.btnWed.isChecked = "WED" in alarm.repeatDays
                        binding.btnThu.isChecked = "THU" in alarm.repeatDays
                        binding.btnFri.isChecked = "FRI" in alarm.repeatDays
                        binding.btnSat.isChecked = "SAT" in alarm.repeatDays
                        binding.btnSun.isChecked = "SUN" in alarm.repeatDays
                    }
                } catch (e: Exception) {
                    handleDataStoreError(e)
                } finally {
                    binding.btnSaveAlarm.isEnabled = true
                }
            }
        } else {
            binding.header.text = "<new alarm>"
        }

        binding.btnSaveAlarm.setOnClickListener { saveAlarm() }
    }

    private fun saveAlarm() {
        var name = binding.etName.text.toString().trim()
        var displayText = binding.etDisplayText.text.toString().trim()

        if (name.isEmpty()/* || displayText.isEmpty()*/) {
            name = "Alarm"
            //Toast.makeText(this, "Missing alarm name", Toast.LENGTH_SHORT).show()
            //return
        }
        if (displayText.isEmpty()) {
            displayText = " "
        }
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@AddAlarmActivity)

                // Prefer the alarm's own owner; fall back to the intent extra (new-alarm path).
                // Re-read from `data` rather than trusting the field set by the onCreate coroutine.
                val editId = intent.getStringExtra("editAlarmId")
                val existing = editId?.let { id -> data.alarms.find { it.id == id } }
                val targetNoteId = existing?.noteId?.takeIf { it.isNotEmpty() }
                    ?: noteId.takeIf { it.isNotEmpty() }
                    ?: ""
                android.util.Log.d("ALARM", "noteId='$noteId' existing=${existingAlarm?.id} notes=${data.notes.size}")
                val note = data.notes.find { it.id == targetNoteId }
                if (note == null) {
                    Toast.makeText(
                        this@AddAlarmActivity,
                        "Could not find associated note",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val repeatDays = buildList {
                    if (binding.btnMon.isChecked) add("MON")
                    if (binding.btnTue.isChecked) add("TUE")
                    if (binding.btnWed.isChecked) add("WED")
                    if (binding.btnThu.isChecked) add("THU")
                    if (binding.btnFri.isChecked) add("FRI")
                    if (binding.btnSat.isChecked) add("SAT")
                    if (binding.btnSun.isChecked) add("SUN")

                }
                val alarm = Alarm(
                    id = existing?.id ?: generateId("t"),
                    noteId = targetNoteId,
                    sectionId = note.sectionId,
                    name = name,
                    timeHour = binding.timePicker.hour,
                    timeMinute = binding.timePicker.minute,
                    displayText = displayText,
                    isActive = existing?.isActive ?: true,
                    repeatDays = repeatDays,
                    createdAt = existing?.createdAt ?: currentTimestamp()
                )

                if (existing != null) {
                    AlarmScheduler.cancel(this@AddAlarmActivity, existing)  // cancel OLD day-set
                    DataStore.updateAlarm(this@AddAlarmActivity, alarm)
                } else {
                    DataStore.addAlarm(this@AddAlarmActivity, alarm)
                }

                if (alarm.isActive) AlarmScheduler.schedule(this@AddAlarmActivity, alarm)
                finish()
            } catch (e: Exception) {
                handleDataStoreError(e)
            }
        }
    }
}