package com.liblens.xyznotes

import android.app.Activity
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Vault picker, creation, and deletion.
 *
 *  Built programmatically rather than as layouts: it is three dialogs, all of
 *  them lists and text, and adding three XML files plus three binding classes
 *  to the build for that is not a trade worth making. */
object VaultUi {

    private fun dp(a: Activity, n: Int) = (n * a.resources.displayMetrics.density).toInt()

    // ── Picker ────────────────────────────────────────────────────────────

    /** Lists vaults, with create at the bottom. Switching locks the current
     *  vault first, so the user always re-authenticates — there is no path
     *  where selecting a vault silently opens it with another vault's session. */
    fun showPicker(activity: Activity, scope: CoroutineScope) {
        val vaults = VaultStore.listVaults()
        val active = BlobStore.activeVault()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8))
            setBackgroundColor(Palette.surface)
        }

        lateinit var dialog: AlertDialog

        vaults.forEach { v ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12))
            }

            val label = TextView(activity).apply {
                text = if (v.id == active) "${v.name}  •" else v.name
                textSize = 16f
                setTextColor(if (v.id == active) Palette.textPrimary else Palette.textDim)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }

            val del = TextView(activity).apply {
                text = "Delete"
                textSize = 13f
                setTextColor(Palette.danger)
                setPadding(dp(activity, 12), 0, dp(activity, 4), 0)
                setOnClickListener {
                    dialog.dismiss()
                    confirmDelete(activity, scope, v)
                }
            }

            row.addView(label)
            // The last vault has no fallback to switch to after deletion, and a
            // vault-less app has no defined state. Hiding the control is
            // clearer than explaining the restriction after the fact.
            if (vaults.size > 1) row.addView(del)

            row.setOnClickListener {
                dialog.dismiss()
                if (v.id != active) switchTo(activity, scope, v.id)
            }
            container.addView(row)
        }

        container.addView(TextView(activity).apply {
            text = "+  New vault"
            textSize = 16f
            setTextColor(Palette.accent)
            setPadding(dp(activity, 12), dp(activity, 16), dp(activity, 12), dp(activity, 12))
            setOnClickListener {
                dialog.dismiss()
                showCreate(activity, scope)
            }
        })

        dialog = ActivityBuilder.dialog(activity)
            .setTitle("Vaults")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
        ActivityBuilder.skin(dialog)
    }

    // ── Switch ────────────────────────────────────────────────────────────

    private fun switchTo(activity: Activity, scope: CoroutineScope, vaultId: String) {
        scope.launch {
            DataStore.switchVault(activity, vaultId)
            activity.startActivity(
                Intent(activity, PassphraseActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            activity.finish()
        }
    }

    // ── Create ────────────────────────────────────────────────────────────

    private fun showCreate(activity: Activity, scope: CoroutineScope) {
        val input = ActivityBuilder.input(activity)
        input.setTextColor(Palette.textPrimary)
        input.setHintTextColor(Palette.textHint)
        input.setBackgroundColor(Palette.input)
        ActivityBuilder.dialog(activity)
            .setTitle("New vault")
            .setMessage(
                "A vault is a separate set of notes with its own passphrase. " +
                        "Notes cannot be seen from another vault."
            )
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Vault" }
                scope.launch {
                    val m = VaultStore.createVault(name)
                    DataStore.switchVault(activity, m.id)
                    activity.startActivity(
                        Intent(activity, PassphraseActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    activity.finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Delete ────────────────────────────────────────────────────────────

    /** Type-the-name confirmation.
     *
     *  The name rather than a fixed phrase: a fixed phrase becomes muscle
     *  memory, and the failure this guards against is deleting the WRONG vault,
     *  not deleting carelessly. Typing the name forces the user to read which
     *  one is selected.
     *
     *  Deletion removes the key file with the blobs, so the contents are
     *  unrecoverable even from a filesystem undelete. The dialog says so. */
    private fun confirmDelete(activity: Activity, scope: CoroutineScope, vault: VaultManifest) {
        val created = if (vault.createdUtc > 0)
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(vault.createdUtc))
        else "unknown"

        val input = ActivityBuilder.input(activity)
        input.setTextColor(Palette.textPrimary)
        input.setHintTextColor(Palette.textHint)
        input.setBackgroundColor(Palette.input)
        lateinit var dialog: AlertDialog

        dialog = ActivityBuilder.dialog(activity)
            .setTitle("Delete vault")
            .setMessage(
                "Vault name: ${vault.name}\nCreated $created\n\n" +
                        "Every note in this vault will be permanently deleted. This cannot be undone.\n\n" +
                        "Enter the vault name to delete:"
            )
            .setView(input)
            .setPositiveButton("Delete", null)   // wired below so it can stay disabled
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.isEnabled = false
            ok.setTextColor(Palette.textDim)

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val match = s.toString() == vault.name
                    ok.isEnabled = match
                    ok.setTextColor(if (match) Palette.danger else Palette.textDim)
                }
            })

            ok.setOnClickListener {
                dialog.dismiss()
                scope.launch {
                    // Lock first if this is the active vault — deleting the
                    // directory out from under an unlocked session would leave
                    // a live data key for content that no longer exists.
                    if (vault.id == BlobStore.activeVault()) {
                        val fallback = VaultStore.listVaults().firstOrNull { it.id != vault.id }
                            ?: return@launch
                        DataStore.switchVault(activity, fallback.id)
                    }
                    VaultStore.deleteVault(vault.id)
                    activity.startActivity(
                        Intent(activity, PassphraseActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    activity.finish()
                }
            }
        }
        dialog.show()
    }
}