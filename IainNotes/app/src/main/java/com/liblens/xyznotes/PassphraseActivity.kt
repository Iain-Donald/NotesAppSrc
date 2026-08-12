package com.liblens.xyznotes

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liblens.xyznotes.crypto.LowMemoryException
import com.liblens.xyznotes.databinding.ActivityPassphraseBinding
import kotlinx.coroutines.launch

class PassphraseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPassphraseBinding
    private var attemptCount = 0
    private var sessionStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        BlobStore.init(this)
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivityPassphraseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySkin()
        // Nothing storage-touching here. onResume gates on permission first.
    }

    override fun onResume() {
        super.onResume()
        if (!sessionStarted) {
            sessionStarted = true
            applySkin()
            beginSession()
        }
    }

    private fun applySkin() {
        binding.root.setBackgroundColor(Palette.background)
        binding.header.setTextColor(Palette.textPrimary)
        binding.tvConfirmLabel.setTextColor(Palette.textPrimary)
        binding.tvStatus.setTextColor(Palette.textDim)

        listOf(binding.etPassphrase, binding.etConfirmPassphrase).forEach {
            it.setTextColor(Palette.textPrimary)
            it.setHintTextColor(Palette.textHint)
            it.backgroundTintList = Palette.tint(Palette.border)
        }

        binding.btnConfirm.backgroundTintList = Palette.tint(Palette.button)
        binding.btnConfirm.setTextColor(Palette.buttonText)
    }

    // ── Session ───────────────────────────────────────────────────────────

    private fun beginSession() {
        val prefs = PreferencesManager.load()
        val isFirstRun = !DataStore.hasContainer()

        if (!prefs.usePassphrase) {
            openWithoutPassphrase(isFirstRun)
            return
        }

        // Reset anything the permission gate may have hidden.
        binding.header.text = if (isFirstRun) "<create passphrase>" else "<enter passphrase>"
        binding.btnConfirm.text = if (isFirstRun) "Create" else "Unlock"
        binding.btnConfirm.isEnabled = true
        binding.tvStatus.visibility = View.GONE

        val showConfirm = if (isFirstRun) View.VISIBLE else View.GONE
        binding.etConfirmPassphrase.visibility = showConfirm
        binding.tvConfirmLabel.visibility = showConfirm

        binding.btnConfirm.setOnClickListener { onConfirm(isFirstRun) }
    }

    private fun openWithoutPassphrase(isFirstRun: Boolean) {
        binding.etPassphrase.visibility = View.GONE
        binding.etConfirmPassphrase.visibility = View.GONE
        binding.tvConfirmLabel.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Opening..."

        lifecycleScope.launch {
            try {
                if (isFirstRun) DataStore.createNew(null)
                else DataStore.unlockWithoutPassphrase()
                startActivity(Intent(this@PassphraseActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                binding.tvStatus.visibility = View.GONE
                binding.btnConfirm.visibility = View.VISIBLE
                // Do not fall through to an empty app — a missing key file on an
                // existing install must be visible, not silently recreated.
                Toast.makeText(
                    this@PassphraseActivity,
                    e.message ?: "Could not open your data",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onConfirm(isFirstRun: Boolean) {
        val passphrase = binding.etPassphrase.text.toString().toCharArray()
        if (passphrase.isEmpty()) {
            Toast.makeText(this, "Passphrase cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (isFirstRun) {
            val confirm = binding.etConfirmPassphrase.text.toString().toCharArray()
            if (!passphrase.contentEquals(confirm)) {
                Toast.makeText(this, "Passphrases do not match", Toast.LENGTH_SHORT).show()
                return
            }
        }

        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text =
            if (isFirstRun) "Setting up encryption..." else "Verifying passphrase..."
        binding.btnConfirm.isEnabled = false

        lifecycleScope.launch {
            try {
                val success = if (isFirstRun) {
                    DataStore.createNew(passphrase); true
                } else {
                    DataStore.unlock(passphrase)
                }

                if (success) {
                    startActivity(Intent(this@PassphraseActivity, MainActivity::class.java))
                    finish()
                } else {
                    onWrongPassphrase()
                }
            } catch (e: LowMemoryException) {
                resetInput()
                Toast.makeText(this@PassphraseActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                resetInput()
                Toast.makeText(
                    this@PassphraseActivity,
                    e.message ?: "Could not open your data",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onWrongPassphrase() {
        attemptCount++
        if (attemptCount >= 5) {
            startLockout()
            return
        }
        val remaining = 5 - attemptCount
        resetInput()
        Toast.makeText(
            this,
            if (remaining > 3) "Incorrect passphrase"
            else "Incorrect — $remaining attempt${if (remaining == 1) "" else "s"} before 5s time lock",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun resetInput() {
        binding.tvStatus.visibility = View.GONE
        binding.btnConfirm.isEnabled = true
    }

    private fun startLockout() {
        binding.etPassphrase.isEnabled = false
        binding.etConfirmPassphrase.isEnabled = false
        binding.btnConfirm.isEnabled = false

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = "Too many attempts — wait ${secondsLeft}s"
            }
            override fun onFinish() {
                attemptCount = 0
                binding.etPassphrase.isEnabled = true
                binding.etConfirmPassphrase.isEnabled = true
                binding.btnConfirm.isEnabled = true
                binding.etPassphrase.text?.clear()
                binding.tvStatus.visibility = View.GONE
                Toast.makeText(this@PassphraseActivity, "You may try again", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}