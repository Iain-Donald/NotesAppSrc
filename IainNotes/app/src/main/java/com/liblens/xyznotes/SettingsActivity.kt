package com.liblens.xyznotes

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.liblens.xyznotes.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var prefs = PreferencesManager.load()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySkin()

        binding.header.text = "<settings>"
        prefs = PreferencesManager.load()
        loadPrefs()

        binding.switchUsePassphrase.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                showSetPassphraseDialog()
            } else {
                showRemovePassphraseDialog()
            }
        }

        binding.switchLockOnClose.setOnCheckedChangeListener { _, checked ->
            prefs = prefs.copy(lockOnClose = checked)
            PreferencesManager.save(prefs)
        }

        binding.btnChangePassphrase.setOnClickListener {
            showChangePassphraseDialog()
        }

        binding.btnExport.setOnClickListener {
            startActivity(Intent(this@SettingsActivity, ExportActivity::class.java))
        }

        var listeningToRadio = false
        binding.radioTheme.setOnCheckedChangeListener { _, checkedId ->
            if (!listeningToRadio) return@setOnCheckedChangeListener
            val theme = when (checkedId) {
                R.id.radioLight -> "light"
                //R.id.radioAmoled -> "amoled"
                else -> "dark"
            }
            ThemeManager.switch(theme)
            recreate()
        }

        // Set the current selection — flag is false so listener ignores this
        binding.radioTheme.check(when (prefs.theme) {
            "light" -> R.id.radioLight
            //"amoled" -> R.id.radioAmoled
            else -> R.id.radioDark
        })

        bindPermissions()

        // Now enable the listener for user interaction
        listeningToRadio = true
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun applySkin() {
        binding.root.setBackgroundColor(Palette.background)
        binding.header.setTextColor(Palette.textPrimary)

        listOf(
            binding.tvPassphraseLabel, binding.tvLockOnCloseLabel,
            binding.tvThemeLabel, binding.tvPermissionsLabel, binding.aboutLabel,
            binding.rowExactAlarms, binding.rowDnd, binding.rowNotifications
        ).forEach { it.setTextColor(Palette.textPrimary) }

        listOf(
            binding.tvExactAlarmsState, binding.tvDndState, binding.tvNotificationsState
        ).forEach { it.setTextColor(Palette.textDim) }

        listOf(binding.btnChangePassphrase, binding.btnExport).forEach {
            it.backgroundTintList = Palette.tint(Palette.button)
            it.setTextColor(Palette.buttonText)
        }

        listOf(binding.divider3, binding.divider4, binding.divider5).forEach {
            it.setBackgroundColor(Palette.divider)
        }

        listOf(binding.radioLight, binding.radioDark).forEach {
            it.setTextColor(Palette.textPrimary)
            it.buttonTintList = Palette.tint(Palette.accent)
        }

        listOf(binding.switchUsePassphrase, binding.switchLockOnClose).forEach {
            it.thumbTintList = Palette.tint(Palette.accent)
            it.trackTintList = Palette.tint(Palette.iconDim)
        }
    }

    private fun bindPermissions() {
        val nm = getSystemService(NotificationManager::class.java)

        binding.rowExactAlarms.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            })
        }
        binding.rowDnd.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        binding.rowNotifications.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            })
        }
    }

    private fun refreshPermissionStates() {
        val nm = getSystemService(NotificationManager::class.java)
        binding.tvExactAlarmsState.text =
            if (AlarmScheduler.canScheduleExact(this)) "Granted" else "Not granted"
        binding.tvDndState.text =
            if (nm.isNotificationPolicyAccessGranted) "Granted" else "Not granted"
        binding.tvNotificationsState.text =
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) "Granted" else "Not granted"
    }

    private fun loadPrefs() {
        binding.switchUsePassphrase.isChecked = prefs.usePassphrase
        binding.switchLockOnClose.isChecked = prefs.lockOnClose
        binding.switchLockOnClose.isEnabled = prefs.usePassphrase
        binding.tvLockOnCloseLabel.alpha = if (prefs.usePassphrase) 1f else 0.4f
        // Show change pw button only when passphrase is enabled.
        binding.btnChangePassphrase.visibility =
            if (prefs.usePassphrase) View.VISIBLE else View.GONE
    }

    private fun showSetPassphraseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_passphrase, null)
        val dialog = ActivityBuilder.dialog(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btnConfirmPassphrase).setOnClickListener {
            val pass = dialogView.findViewById<EditText>(R.id.etNewPassphrase)
                .text.toString().toCharArray()
            val confirm = dialogView.findViewById<EditText>(R.id.etConfirmPassphrase)
                .text.toString().toCharArray()

            if (pass.isEmpty()) {
                Toast.makeText(this, "Passphrase cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!pass.contentEquals(confirm)) {
                Toast.makeText(this, "Passphrases do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Re-encrypt existing data with new passphrase
            lifecycleScope.launch {
                try {
                    DataStore.setPassphrase(pass)
                    prefs = prefs.copy(usePassphrase = true)
                    PreferencesManager.save(prefs)
                    binding.switchLockOnClose.isEnabled = true
                    binding.tvLockOnCloseLabel.alpha = 1f
                    dialog.dismiss()
                }  catch (e: Exception) {
                    handleDataStoreError(e)
                }
            }
        }

        dialogView.findViewById<Button>(R.id.btnCancelPassphrase).setOnClickListener {
            binding.switchUsePassphrase.isChecked = false
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showRemovePassphraseDialog() {
        AlertDialog.Builder(this@SettingsActivity)
            .setTitle("Remove passphrase?")
            .setMessage("Your data will be stored without encryption.")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    DataStore.removePassphrase()
                    prefs = prefs.copy(usePassphrase = false, lockOnClose = false)
                    PreferencesManager.save(prefs)
                    binding.switchLockOnClose.isChecked = false
                    binding.switchLockOnClose.isEnabled = false
                    binding.tvLockOnCloseLabel.alpha = 0.4f
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                binding.switchUsePassphrase.isChecked = true
            }
            .show()
    }

    private fun showChangePassphraseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_passphrase, null)
        val dialog = AlertDialog.Builder(this@SettingsActivity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val etCurrent = dialogView.findViewById<EditText>(R.id.etCurrentPassphrase)
        val etNew = dialogView.findViewById<EditText>(R.id.etNewPassphrase)
        val etConfirm = dialogView.findViewById<EditText>(R.id.etConfirmNewPassphrase)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tvChangeStatus)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmChange)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelChange)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnConfirm.setOnClickListener {
            val current = etCurrent.text.toString().toCharArray()
            val new = etNew.text.toString().toCharArray()
            val confirm = etConfirm.text.toString().toCharArray()

            if (current.isEmpty()) {
                Toast.makeText(this@SettingsActivity,
                    "Current passphrase is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (new.isEmpty()) {
                Toast.makeText(this@SettingsActivity,
                    "New passphrase cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!new.contentEquals(confirm)) {
                Toast.makeText(this@SettingsActivity,
                    "New passphrases do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnConfirm.isEnabled = false
            etCurrent.isEnabled = false
            etNew.isEnabled = false
            etConfirm.isEnabled = false
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = "Verifying & re-encrypting..."

            tvStatus.post {
                lifecycleScope.launch {
                    try {
                        val success = DataStore.changePassphrase(current, new)
                        if (success) {
                            dialog.dismiss()
                            Toast.makeText(this@SettingsActivity,
                                "Passphrase changed", Toast.LENGTH_SHORT).show()
                        } else {
                            tvStatus.visibility = View.GONE
                            btnConfirm.isEnabled = true
                            etCurrent.isEnabled = true
                            etNew.isEnabled = true
                            etConfirm.isEnabled = true
                            Toast.makeText(this@SettingsActivity,
                                "Current passphrase is incorrect", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        handleDataStoreError(e)
                    }
                }
            }
        }

        dialog.show()
    }
}