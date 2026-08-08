package com.liblens.xyznotes

import android.icu.util.Calendar
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.liblens.xyznotes.databinding.ActivityAlarmAlertBinding

class AlarmAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmAlertBinding
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityAlarmAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val alarmName = intent.getStringExtra("alarmName") ?: "Alarm"
        val displayText = intent.getStringExtra("displayText") ?: ""
        val alarmId = intent.getStringExtra("alarmId") ?: ""

        binding.tvAlarmName.text = alarmName
        binding.tvDisplayText.text = displayText

        val now = Calendar.getInstance()
        binding.tvTime.text = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))

        startAlarmSound()

        binding.btnDismiss.setOnClickListener {
            stopAlarmSound()
            finish()
        }

        val repeatDays = intent.getStringArrayExtra("repeatDays")?.toList() ?: emptyList()

        binding.btnSnooze.setOnClickListener {
            stopAlarmSound()
            val snoozeAt = System.currentTimeMillis() + 10 * 60 * 1000L
            AlarmScheduler.scheduleAt(
                this,
                Alarm(
                    id = alarmId,
                    noteId = "", sectionId = "",
                    name = alarmName,
                    timeHour = 0, timeMinute = 0,          // unused by scheduleAt
                    displayText = displayText,
                    isActive = true,
                    repeatDays = repeatDays
                ),
                snoozeAt
            )
            finish()
        }
    }

    private fun startAlarmSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer().apply {
            setDataSource(applicationContext, alarmUri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            prepare()
            start()
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.apply { if (isPlaying) stop(); release() }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }
}