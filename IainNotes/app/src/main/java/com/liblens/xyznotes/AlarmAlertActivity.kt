package com.liblens.xyznotes

import android.icu.util.Calendar
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.liblens.xyznotes.databinding.ActivityAlarmAlertBinding
import android.content.Intent

class AlarmAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmAlertBinding
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivityAlarmAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySkin()
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

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

    private fun applySkin() {
        binding.root.setBackgroundColor(Palette.background)
        binding.tvTime.setTextColor(Palette.textPrimary)
        binding.tvAlarmName.setTextColor(Palette.textPrimary)
        binding.tvDisplayText.setTextColor(Palette.textDim)
        listOf(binding.btnSnooze, binding.btnDismiss).forEach {
            it.backgroundTintList = Palette.tint(Palette.button)
            it.setTextColor(Palette.buttonText)
        }
    }

    private var vibrator: Vibrator? = null

    private fun startVibration() {
        val vm = getSystemService(VibratorManager::class.java) ?: return
        vibrator = vm.defaultVibrator
        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(
            VibrationEffect.createWaveform(pattern, 0),   // 0 = repeat from index 0
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
        )
    }
    private var timeoutHandler: android.os.Handler? = null

    private fun startAlarmSound() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        try {
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
        } catch (e: Exception) {
            // A missing or unplayable ringtone must not kill the alert.
            android.util.Log.w("XYNC", "alarm sound failed", e)
        }
        startVibration()

        // Auto-dismiss so an unattended alarm doesn't drain the battery.
        timeoutHandler = android.os.Handler(mainLooper).also {
            it.postDelayed({ stopAlarmSound(); finish() }, 5 * 60 * 1000L)
        }
    }

    private fun stopAlarmSound() {
        timeoutHandler?.removeCallbacksAndMessages(null)
        timeoutHandler = null
        try { mediaPlayer?.apply { if (isPlaying) stop(); release() } } catch (_: Exception) { }
        mediaPlayer = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) stopAlarmSound()
    }
}