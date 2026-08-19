package com.liblens.xyznotes

import android.app.Application

/** Single initialisation point for process-wide state.
 *
 *  Runs before any Activity, Service or BroadcastReceiver in this process.
 *  That ordering is the whole point: every entry path — launcher, notification
 *  tap, alarm broadcast, boot broadcast, process-death restore — arrives here
 *  first, so no entry point needs its own init call and none can forget one.
 *
 *  BlobStore.init() is idempotent and is deliberately left in the receivers as
 *  well: a receiver can be the reason the process is created, and while
 *  onCreate here still runs first, the redundant call costs nothing and keeps
 *  the receivers independently correct if this class is ever removed. */
class XyzNotesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BlobStore.init(this)
        // Every install has at least one vault. Existing single-vault data has
        // no vaults/ directory, so this also creates the manifest for it.
        if (VaultStore.listVaults().isEmpty()) {
            VaultStore.createVault("Notes", VaultStore.DEFAULT_VAULT_ID)
        }
        // setActiveVault, NOT switchVault — nothing is unlocked yet, there is
        // no previous vault to tear down, and the incoming vault's alarms are
        // already armed from the last session or from BootReceiver.
        VaultStore.loadIndex().lastOpened
            ?.takeIf { VaultStore.exists(it) }
            ?.let { BlobStore.setActiveVault(it) }
        // Reads preferences.json off the main thread's critical path is not
        // possible here — Palette must be correct before the first setContentView.
        // The file is a few hundred bytes; this is a deliberate accepted cost.
        ThemeManager.apply()
        NoteNotificationManager.createChannel(this)
        AlarmNotifier.createChannel(this)
    }
}