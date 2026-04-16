package com.example.iainnotes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.iainnotes.databinding.ActivityExportBinding
import kotlinx.coroutines.launch

class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding
    private val prefs by lazy { PreferencesManager.load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply()
        super.onCreate(savedInstanceState)

        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.header.text = "<export>"

        // Only show encrypted option if passphrase is set
        if (!prefs.usePassphrase) {
            binding.btnExportEncrypted.visibility = View.GONE
            binding.tvEncryptedDesc.visibility = View.GONE
        }

        binding.btnExportPlain.setOnClickListener {
            showExportConfirmDialog(encrypted = false)
        }

        binding.btnExportEncrypted.setOnClickListener {
            showExportConfirmDialog(encrypted = true)
        }
    }

    private fun showExportConfirmDialog(encrypted: Boolean) {
        val title = if (encrypted) "Export encrypted" else "Export decrypted"
        val message = if (encrypted)
            "Exports as IainNotes-export.tar.enc — requires your passphrase to open on any device."
        else
            "Exports as IainNotes-export.tar — contents are readable by anyone with the file."

        AlertDialog.Builder(this@ExportActivity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Export") { _, _ ->
                performExport(encrypted)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performExport(encrypted: Boolean) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = if (encrypted) "Encrypting..." else "Packing..."
        binding.btnExportPlain.isEnabled = false
        binding.btnExportEncrypted.isEnabled = false

        binding.tvStatus.post {
            lifecycleScope.launch {
                try {
                    val file = DataStore.export(encrypted)
                    binding.tvStatus.text = "Exported to ${file.absolutePath}"
                    binding.btnExportPlain.isEnabled = true
                    binding.btnExportEncrypted.isEnabled = true

                    // Offer to share via system share sheet
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@ExportActivity,
                        "${packageName}.provider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share export via"))

                } catch (e: Exception) {
                    binding.tvStatus.text = "Export failed"
                    binding.btnExportPlain.isEnabled = true
                    binding.btnExportEncrypted.isEnabled = true
                    handleDataStoreError(e)
                }
            }
        }
    }
}