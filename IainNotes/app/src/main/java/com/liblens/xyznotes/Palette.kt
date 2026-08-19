package com.liblens.xyznotes

import android.content.res.ColorStateList

object Palette {

	// ══ Category colors ═══════════════════════════════════════════════════
	// Theme-independent by necessity: colorId is persisted in map.json, so a
	// given id must render the same color forever, in any theme.

	const val NONE = 0

	// Index in this array == colorId - 1. Append only; never reorder or
	// remove, or stored colorIds will point at different colors.
	private val colors = intArrayOf(
		0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
		0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF039BE5.toInt(), 0xFF00ACC1.toInt(),
		0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFF7CB342.toInt(), 0xFFC0CA33.toInt(),
		0xFFFDD835.toInt(), 0xFFFFB300.toInt(), 0xFFFB8C00.toInt(), 0xFFF4511E.toInt(),
		0xFF6D4C41.toInt(), 0xFF757575.toInt(), 0xFF546E7A.toInt(), 0xFFAD1457.toInt(),
		0xFF4527A0.toInt(), 0xFF00695C.toInt(), 0xFF2E7D32.toInt(), 0xFFEF6C00.toInt()
	)

	val ids: List<Int> get() = (1..colors.size).toList()

	fun colorOf(id: Int): Int =
		if (id in 1..colors.size) colors[id - 1] else 0


	// ══ Skin ══════════════════════════════════════════════════════════════
	// Every themed color in the app, resolved here and applied programmatically.
	// No theme attributes, no resource qualifiers, no tint compositing:
	// what is set from here is what renders.

	var dark: Boolean = true
		private set

	fun setDark(value: Boolean) { dark = value }

	private fun pick(darkValue: Long, lightValue: Long): Int =
		(if (dark) darkValue else lightValue).toInt()

	val black = 0x00000000

	// ── Surfaces ──────────────────────────────────────────────────────
	val background get() = pick(0xFF000000, 0xFFFAF5F1)
	val surface get() = pick(0xFF141519, 0xFFE5E1DD)   // was 0xFF404040 / 0xFFDDDDDD
	val input      get() = pick(0xFF1A1B1E, 0xFFE0E0DA)
	val divider    get() = pick(0xFFFFB300, 0xFFDDDDDD)
	val dividerLightInvis get() = pick(0xFFFFB300, 0xFFFAF5F1) // same as background in light mode.

	// ── Text ──────────────────────────────────────────────────────────
	val textPrimary   get() = pick(0xFFFAFAFA, 0xFF111111)
	val textSecondary get() = pick(0xFFFFB300, 0xFF555550)
	val textHint      get() = pick(0xFFFAFAFA, 0xFF999990)
	val textBody      get() = pick(0xFFDDDDDD, 0xFF1A1A1A)
	val textDim       get() = pick(0xFFCCCCCC, 0xFF555555)

	// ── Icons / controls ──────────────────────────────────────────────
	val icon       get() = pick(0xFFEEEEEE, 0xFF333333)
	val iconDim    get() = pick(0xFFAAAAAA, 0xFF888888)
	val accent     get() = pick(0xFFFFB300, 0xFFCC8F00)
	val accent2 get() = pick(0xFF4FC3F7, 0xFF1B7FB0)
	val border     get() = pick(0xFFEEEEEE, 0xFFCCCCCC)

	// ── Buttons ───────────────────────────────────────────────────────
	val button     get() = pick(0xFFEC727B, 0xFFC94F5A)
	val buttonText get() = pick(0xFF111111, 0xFFFFFFFF)

	// ── Badges ────────────────────────────────────────────────────────
	// Identical in both themes; kept here so nothing reaches into colors.xml.
	val badgeActive   = 0xFFFF5252.toInt()
	val badgeInactive = 0xFF888888.toInt()

	// Fixed rather than pick()'d — both read on either skin, and semantic
	// colours shouldn't invert with the theme.
	val STATE_GRANTED = 0xFF4CAF50.toInt()
	val STATE_DENIED  = 0xFFE53935.toInt()

	val danger = 0xFFE53935.toInt()   // fixed: semantic, must not invert with skin


	fun tint(color: Int): ColorStateList = ColorStateList.valueOf(color)
}