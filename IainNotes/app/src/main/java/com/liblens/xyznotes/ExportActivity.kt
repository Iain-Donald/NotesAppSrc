package com.liblens.xyznotes

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.liblens.xyznotes.crypto.CryptoException
import com.liblens.xyznotes.databinding.ActivityExportBinding
import kotlinx.coroutines.launch

class ExportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExportBinding
    private val prefs by lazy { PreferencesManager.load() }
    private var pendingEncrypted = false

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-tar")
    ) { uri -> if (uri != null) writeTo(uri) else resetButtons() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.apply()
        binding = ActivityExportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.header.text = "<export>"

        if (!prefs.usePassphrase) {
            binding.btnExportEncrypted.visibility = View.GONE
            binding.tvEncryptedDesc.visibility = View.GONE
        }
        binding.btnExportPlain.setOnClickListener { confirm(false) }
        binding.btnExportEncrypted.setOnClickListener { confirm(true) }
    }

    private fun confirm(encrypted: Boolean) {
        val message = if (encrypted)
            "Saves a .enc.tar containing your encrypted notes and key file. " +
                    "Your passphrase is required to open it — treat the file as sensitive."
        else
            "Saves a plain .tar. Contents are readable by anyone with the file."

        ActivityBuilder.dialog(this)
            .setTitle(if (encrypted) "Export encrypted" else "Export decrypted")
            .setMessage(message)
            .setPositiveButton("Choose location") { _, _ ->
                pendingEncrypted = encrypted
                binding.btnExportPlain.isEnabled = false
                binding.btnExportEncrypted.isEnabled = false
                createDocument.launch(Exporter.suggestedName(encrypted))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun writeTo(uri: Uri) {
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = if (pendingEncrypted) "Encrypting..." else "Packing..."

        lifecycleScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    DataStore.exportTo(out, pendingEncrypted)
                } ?: throw CryptoException("Could not open the chosen location for writing")
                binding.tvStatus.text = "Exported"
            } catch (e: Exception) {
                binding.tvStatus.text = "Export failed"
                handleDataStoreError(e)
            } finally {
                resetButtons()
            }
        }
    }

    private fun resetButtons() {
        binding.btnExportPlain.isEnabled = true
        binding.btnExportEncrypted.isEnabled = true
    }
}