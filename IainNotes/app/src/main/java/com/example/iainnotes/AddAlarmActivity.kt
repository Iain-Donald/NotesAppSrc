package com.example.iainnotes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iainnotes.databinding.ActivityAddAlarmBinding
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
            lifecycleScope.launch {
                try {
                    val appData = DataStore.load(this@AddAlarmActivity)
                    existingAlarm = appData.alarms.find { it.id == editAlarmId }
                    existingAlarm?.let { alarm ->
                        noteId = alarm.noteId
                        binding.header.text = "<edit/${alarm.name}>"
                        binding.etName.setText(alarm.name)
                        binding.timePicker.hour = alarm.timeHour
                        binding.timePicker.minute = alarm.timeMinute
                        binding.etDisplayText.setText(alarm.displayText)
                        binding.switchActive.isChecked = alarm.isActive
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
                }
            }
        } else {
            binding.header.text = "<new alarm>"
        }

        binding.btnSaveAlarm.setOnClickListener { saveAlarm() }
    }

    private fun saveAlarm() {
        val name = binding.etName.text.toString().trim()
        val displayText = binding.etDisplayText.text.toString().trim()

        if (name.isEmpty() || displayText.isEmpty()) {
            Toast.makeText(this, "Name and display text are required", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val data = DataStore.load(this@AddAlarmActivity)
                val note = data.notes.find { it.id == noteId }
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
                    id = generateId("t"),
                    noteId = noteId,
                    sectionId = note.sectionId,
                    name = name,
                    timeHour = binding.timePicker.hour,
                    timeMinute = binding.timePicker.minute,
                    displayText = displayText,
                    isActive = binding.switchActive.isChecked,
                    repeatDays = repeatDays
                )

                if (existingAlarm != null) {
                    AlarmScheduler.cancel(this@AddAlarmActivity, existingAlarm!!)
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