package com.liblens.xyznotes

/** Static reference text shown by the Read me section in Settings.
 *
 *  Kept in Kotlin rather than strings.xml deliberately: this is long-form prose
 *  that will be edited often and never localised, and XML escaping makes that
 *  painful. If localisation is ever added, this is the file to move. */
object ReadMe {
    val WARNINGS = """
		ABOUT THIS APP

This app contains no advertising, no analytics, no crash reporting,
and no telemetry of any kind. Nothing it stores leaves your device
unless you explicitly export it. None of its security model relies on secrecy to be secure. If you have any feedback, please add it to the GitHub link at the bottom of the settings page. 

When a password is enabled, your data is unlocked with the following process: password (not stored anywhere here) -> key derivation function -> decrypts the 256 bit data key -> decrypts your data via stream cipher function. This process takes place in memory. Your data is also decrypted into memory. When a password is enabled, no decrypted data is cached or stored in storage. Encrypted temp files followed by an atomic swap operation are used for all edits, which make recovery in the event of crashes or power loss possible. All relevant information required for you to access your data from exported encrypted files such as the header and cryptography structure are documented in the event this app is lost to time. A an example CLI based app for is already provided on the GitHub :)

YOUR DATA AND UNINSTALLING

		XyzNotes stores your notes in the app's allotted storage area. Android
		deletes that area completely when the app is uninstalled or when you
		use "Clear storage" in system settings. There is no alternative data location apart from that and your exports.

		This app opts out of Android's automatic cloud backup. As of now, you may sync the files yourself after exporting them from within the app. Exported data may be decrypted using your associated vault password with ___.

		If you have set a passphrase, there is a second consequence. Your notes
		are encrypted with a key that is itself stored in the app's storage. If
		that storage is deleted, the key is gone, and the notes cannot be
		decrypted. There is no recovery process. For exported notes, recovery is possible if you have the key. 


		BEFORE YOU UNINSTALL

		To keep your notes:

		1. Open Settings.
		2. Prepare to export: If you have a passphrase set, your first option is to turn it off. This decrypts your notes for easy access after exporting. Your second option option is to leave it on. This way, exporting your notes will come with a key. You will need this key and your password to unlock your notes after exporting them. 
		3. Tap "Export data" and save the file somewhere outside the app. Virtually anywhere you choose here is safe from deletion upon uninstalling the app.
		4. Confirm the exported file exists, is not empty, and is accessible before you
		   uninstall.

		Exporting regularly is worth doing even if you have no plans to
		uninstall. There are many ways to lose your phone data or your phone itself!
	""".trimIndent()

    val LIBRARIES = """
		LIBRARIES AND LICENCES

		
        Here are all coding libraries used in this app, accompanied by justification for their use. All other functionality is manually implemented. 

		─────────────────────────────────────────────

		libsodium
		Licence: ISC (
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


		LICENSES
	""".trimIndent()

    val CRYPTOGRAPHY = """
HOW YOUR NOTES ARE PROTECTED

This app does not intend to rely on secrecy to achieve security. The design of its security will be discovered either way. This information is provided so you can access your data without relying on this app. 

WHEN THERE IS NO PASSPHRASE

Notes are written to disk as plain files in the app's private storage
area. Other apps cannot read them, but anyone with physical access to
an unlocked device, or with the ability to read the phone's storage, can. 


WHEN THERE IS A PASSPHRASE

Two separate keys are involved.

The data key is a random 256-bit value generated on first use. It encrypts your notes. It never changes unless you remove and re-add a passphrase, and it is never shown to you.

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


		THE ENCRYPTION THEORY

		Notes are encrypted with the XChaCha20-Poly1305 stream cipher. This is stream cipher, safer than the widely used but flawed AES-GCM, which is excellent in theory, but almost always flawed in its implementation. Despite Government, Corporate, and Android developer recommendations, requirements, and guidelines, I decided not to trust AES-GCM. Even without hardware acceleration, XChaCha20-Poly1305 is currently faster and more secure than AES-GCM. AEGIS-256 was another consideration, but without hardware acceleration, AEGIS-256 also becomes slow and vulnerable. In the best case for AEGIS-256 compared to the actual XChaCha20-Poly1305 cryptography active here, we are playing with two infinities so vast that any forseeable quantum attacks or attacks by the world's computing power are not a concern for your notes! Breaking a flawless AES-GCM implementation is not so far fetched, and consistent incremental progress has dropped it to supercomputer territory as of today. Our computers have many common channels of leaking data from AES implementations, resulting in countless increasingly trivial breaks in practice. 

THE SECURITY MODEL ITSELF

		Each note is encrypted separately using XChaCha20-Poly1305. The AAD (Additional Associated Data) includes the file header metadata, creating a unique nonce that is bound to its own location. A note file copied to a different location, name changed, a single bit changes anywhere in its header or data will not decrypt. Tampered or corrupted data will simply not decrypt. The risk of data loss is mitigated by each note using its own encryption, and the export/import/merge features. 


		WHILE YOU ARE USING THE APP

		Notes are decrypted in memory only when displayed, and the data key is
		wiped from memory when the app locks. Reading, editing, and saving all
		pass through the same encryption path. There is no cache of decrypted
		notes on disk.

		Every save writes to a temporary file which then atomically replaces the previous version. If the device loses power mid-save, you always keep one of these versions, never a corrupted mixture. 

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