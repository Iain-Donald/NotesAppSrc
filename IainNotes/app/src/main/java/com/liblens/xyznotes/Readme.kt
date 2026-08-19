package com.liblens.xyznotes

/** Static reference text shown by the Read me section in Settings.
 *
 *  Kept in Kotlin rather than strings.xml deliberately: this is long-form prose
 *  that will be edited often and never localised, and XML escaping makes that
 *  painful. If localisation is ever added, this is the file to move. */
object ReadMe {
    val WARNINGS = """
		ABOUT THIS APP

YOUR DATA AND UNINSTALLING

		XyzNotes stores your notes in the app's alloted storage area. Android
		deletes that area completely when the app is uninstalled or when you
		use "Clear storage" in system settings. Nothing is kept on a server,
		and there is no account to restore from.

		This app also opts out of Android's automatic cloud backup. As of now, you may sync the files yourself after exporting them from within the app. The trade-off is that there is no safety net if the app's storage is removed.

		If you have set a passphrase, there is a second consequence. Your notes
		are encrypted with a key that is itself stored in the app's storage. If
		that storage is deleted, the key is gone, and the notes cannot be
		decrypted. There is no recovery process. 


		BEFORE YOU UNINSTALL

		To keep your notes:

		1. Open Settings.
		2. If you have a passphrase set, turn it off. This decrypts your notes
		   so they no longer depend on a key that is about to be deleted.
		3. Tap "Export data" and save the file somewhere outside the app —
		   your Documents folder, a cloud drive, or an SD card. The export
		   location you choose is yours and is not removed when the app is
		   uninstalled.
		4. Confirm the exported file exists and is not empty before you
		   uninstall.

		Doing steps 2 and 3 in that order matters. 

		Exporting regularly is worth doing even if you have no plans to
		uninstall. Phones get lost, broken, and factory-reset without warning.
	""".trimIndent()

    val LIBRARIES = """
		LIBRARIES AND LICENCES

		XyzNotes deliberately uses few dependencies. Every one adds download
		size, attack surface, and a release cadence outside our control, so
		each is here because writing it by hand would have been worse.

		This app contains no advertising, no analytics, no crash reporting,
		and no telemetry of any kind. Nothing it stores leaves your device
		unless you explicitly export it.


		─────────────────────────────────────────────

		libsodium
		Licence: ISC
		Used for: all cryptography — XChaCha20-Poly1305 encryption and Argon2id
		passphrase hashing. Compiled from source and linked statically, so the
		exact version in this app is fixed at build time rather than supplied
		by the system.

		─────────────────────────────────────────────

		Kotlin Standard Library
		Licence: Apache 2.0
		Used for: the language runtime.

		─────────────────────────────────────────────

		kotlinx.serialization
		Licence: Apache 2.0
		Used for: reading and writing the JSON files that hold your note index,
		alarms, and settings.

		─────────────────────────────────────────────

		kotlinx.coroutines
		Licence: Apache 2.0
		Used for: running slow work — decryption, file reads, passphrase
		hashing — off the main thread so the interface stays responsive.

		─────────────────────────────────────────────

		AndroidX libraries
		Licence: Apache 2.0
		Used for: core interface components. Specifically AppCompat (activities
		and dialogs), ConstraintLayout and CardView (screen layout),
		RecyclerView (scrolling lists), Core-KTX (Android API helpers), and
		Lifecycle (tying background work to screen lifetime).

		─────────────────────────────────────────────


		ABOUT THESE LICENCES

		Apache 2.0 and ISC are permissive open-source licences. They allow this
		app to include the software commercially, and they require that the
		original copyright notices and licence terms are made available — which
		is what this page is for. Neither licence gives the authors any claim
		over your notes or over this app.

		Full licence texts are available from each project's own website.
	""".trimIndent()

    val CRYPTOGRAPHY = """
		HOW YOUR NOTES ARE PROTECTED

		This page describes exactly what the app does. Publishing it is
		deliberate. A cryptographic design that depends on being kept secret is
		a broken one — security comes from the key, not from hiding the method.
		Describing it openly also lets anyone who knows the subject check the
		claims.


		THE SHORT VERSION

		If you set a passphrase, your notes are encrypted with modern,
		well-regarded algorithms, and the passphrase is the only way in. If you
		do not set one, your notes are stored unencrypted and are protected
		only by Android's own separation between apps.


		WHEN THERE IS NO PASSPHRASE

		Notes are written to disk as plain files in the app's private storage
		area. Other apps cannot read them, but anyone with physical access to
		an unlocked device, or with the ability to read the phone's storage,
		can. Choose a passphrase if that matters to you.


		WHEN THERE IS A PASSPHRASE

		Two separate keys are involved.

		The data key is a random 256-bit value generated on first use. It
		encrypts your notes. It never changes unless you remove and re-add a
		passphrase, and it is never shown to you.

		The passphrase key is derived from what you type, using Argon2id — a
		function designed to be slow and memory-hungry specifically to make
		guessing expensive. It uses roughly 64 MB of memory per attempt, which
		is why unlocking takes a moment. That cost falls on anyone trying to
		guess your passphrase far more heavily than it falls on you.

		The passphrase key encrypts the data key. The data key encrypts your
		notes. This is why changing your passphrase is instant: only the small
		wrapped key is rewritten, not every note.

		Your passphrase itself is never stored anywhere, in any form. The app
		cannot tell you what it was and cannot verify it except by attempting
		to decrypt.


		THE ENCRYPTION ITSELF

		Notes are encrypted with XChaCha20-Poly1305. This is authenticated
		encryption: as well as hiding the contents, it detects tampering. A
		note that has been altered on disk fails to decrypt rather than
		decrypting to something wrong.

		Each note is encrypted separately, with its own random nonce, and is
		bound to its own location. A note file copied to a different name will
		not decrypt. This limits the damage from a corrupted file to that one
		note rather than to everything.


		WHILE YOU ARE USING THE APP

		Notes are decrypted in memory only when displayed, and the data key is
		wiped from memory when the app locks. Reading, editing, and saving all
		pass through the same encryption path — there is no cache of decrypted
		notes on disk.

		Every save writes to a temporary file first and then replaces the
		original in a single step. If the device loses power mid-save, you
		keep either the old note or the new one, never a corrupted mixture.


		EXPORTS

		A plain export decrypts everything and writes readable files. Anyone
		who obtains that file can read your notes. Store it accordingly.

		An encrypted export keeps everything encrypted and includes the
		wrapped key, so it can only be opened with your passphrase.


		WHAT THIS DOES NOT PROTECT AGAINST

		Being honest about limits matters more than the reassuring version.

		Encryption protects notes at rest. It cannot help if your device is
		compromised while the app is unlocked, if your passphrase is weak
		enough to guess, if it is stolen by malware or a camera over your
		shoulder, or if someone with your unlocked phone simply opens the app.

		If you forget your passphrase, your notes are unrecoverable. This is
		not a policy that can be appealed — the app genuinely does not have
		the information needed to help.
	""".trimIndent()
}