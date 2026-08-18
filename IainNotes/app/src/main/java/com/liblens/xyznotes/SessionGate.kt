package com.liblens.xyznotes

import android.app.Activity
import android.content.Intent

/** Guards every Activity that touches DataStore against being entered while
 *  the store is locked.
 *
 *  The launcher path is safe by construction: PassphraseActivity is the
 *  LAUNCHER activity, so it always runs first. Every other path is not —
 *  a notification tap, an alarm, or a process-death restore can recreate any
 *  Activity in the back stack directly, with DataStore locked and no
 *  passphrase in memory.
 *
 *  Must be called from BOTH onCreate and onResume. finish() does not abort
 *  the lifecycle: a finished Activity still runs onStart and onResume before
 *  it is destroyed, so an onResume that loads data will do so against a
 *  locked store even though onCreate correctly bailed out. */
object SessionGate {

    const val EXTRA_DESTINATION = "xyz.destination"

    /** Returns true when the caller has been redirected, or is already on its
     *  way out, and must stop. Idempotent — safe to call on every callback. */
    fun gate(activity: Activity): Boolean {
        // Already redirected by a previous call in this lifecycle.
        if (activity.isFinishing || activity.isDestroyed) return true
        if (DataStore.isUnlocked()) return false

        // Carry the original intent — component, extras and all — so the
        // user lands where they tapped rather than on a bare note list.
        val destination = Intent(activity.intent).apply { flags = 0 }

        activity.startActivity(
            Intent(activity, PassphraseActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        activity.finish()
        return true
    }

    /** Called by PassphraseActivity once the store is unlocked.
     *  Builds MainActivity beneath the destination so Back behaves normally. */
    fun resume(activity: Activity, destination: Intent?) {
        val main = Intent(activity, MainActivity::class.java)
        if (destination == null || destination.component == null ||
            destination.component == main.component
        ) {
            activity.startActivity(main)
        } else {
            activity.startActivities(arrayOf(main, destination))
        }
        activity.finish()
    }
}