package com.example.iainnotes

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iainnotes.databinding.ActivityPassphraseBinding
//import com.example.iainnotes.DataStore
import kotlinx.coroutines.launch

class PassphraseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPassphraseBinding
    private var attemptCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityPassphraseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PreferencesManager.load()

        if (!prefs.usePassphrase) {
            DataStore.unlockWithoutPassphrase(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val isFirstRun = !DataStore.hasContainer()

        binding.header.text = if (isFirstRun) "<create passphrase>" else "<enter passphrase>"
        binding.btnConfirm.text = if (isFirstRun) "Create" else "Unlock"
        binding.tvStatus.visibility = View.GONE

        if (isFirstRun) {
            binding.etConfirmPassphrase.visibility = View.VISIBLE
            binding.tvConfirmLabel.visibility = View.VISIBLE
        }

        binding.btnConfirm.setOnClickListener {
            val passphrase = binding.etPassphrase.text.toString().toCharArray()
            if (passphrase.isEmpty()) {
                Toast.makeText(this, "Passphrase cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isFirstRun) {
                val confirm = binding.etConfirmPassphrase.text.toString().toCharArray()
                if (!passphrase.contentEquals(confirm)) {
                    Toast.makeText(this, "Passphrases do not match", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            binding.tvStatus.visibility = View.VISIBLE
            binding.tvStatus.text = if (isFirstRun) "Encrypting..." else "Verifying passphrase..."
            binding.btnConfirm.isEnabled = false

            binding.tvStatus.post {
                val success = if (isFirstRun) {
                    DataStore.unlock(passphrase)
                    lifecycleScope.launch {DataStore.initEmpty(this@PassphraseActivity)}
                    true
                } else {
                    DataStore.unlock(passphrase)
                }

                if (success) {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    attemptCount++
                    if (attemptCount >= 5) {
                        startLockout()
                    } else {
                        val remaining = 5 - attemptCount
                        binding.tvStatus.visibility = View.GONE
                        binding.btnConfirm.isEnabled = true
                        if (remaining >3) {
                            Toast.makeText(
                                this,
                                "Incorrect passphrase",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Incorrect — $remaining attempt${if (remaining == 1) "" else "s"} before 5s time lock",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
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
                binding.btnConfirm.isEnabled = true
                binding.etPassphrase.text?.clear()
                binding.tvStatus.visibility = View.GONE
                Toast.makeText(
                    this@PassphraseActivity,
                    "You may try again",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }
}