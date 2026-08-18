package com.liblens.xyznotes

import android.util.Log

/** Fail-loud policy.
 *
 *  A bug or corrupted state terminates the process. Only a condition that is
 *  genuinely handled — one where continuing produces correct behaviour — is
 *  allowed to log quietly. There is no third category: a caught exception is
 *  either recovered from (use [quiet]) or it is a defect (use [hard]).
 *
 *  Rationale: silent swallowing turns a reproducible crash into an
 *  unreproducible data-loss report from a user who cannot tell you what
 *  happened. */
object Fail {

    private const val TAG = "XYNC"

    /** Unrecoverable. Rethrows; the process dies with the original stack. */
    fun hard(what: String, cause: Throwable): Nothing {
        Log.e(TAG, "FATAL: $what", cause)
        throw IllegalStateException(what, cause)
    }

    /** Unrecoverable, no underlying exception. */
    fun hard(what: String): Nothing {
        Log.e(TAG, "FATAL: $what")
        throw IllegalStateException(what)
    }

    /** Genuinely handled: the caller has a correct fallback and continues.
     *  Every call site must be able to answer "what does the user see now?" */
    fun quiet(what: String, cause: Throwable? = null) {
        if (cause == null) Log.i(TAG, what) else Log.i(TAG, what, cause)
    }

    /** Handled, but suspicious — degraded behaviour the user may notice. */
    fun warn(what: String, cause: Throwable? = null) {
        if (cause == null) Log.w(TAG, what) else Log.w(TAG, what, cause)
    }
}