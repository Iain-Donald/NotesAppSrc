package com.liblens.xyznotes.editor

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * A multiline EditText whose viewport moves ONLY when you type, and then by the
 * minimum amount needed to bring the caret back to the nearest edge.
 *
 * Stock EditText fights you in two independent ways. They had to be found
 * separately, and crucially only ONE of them is a scroll:
 *
 *  1. TextView.bringPointIntoView(offset)
 *     Scrolls the view to keep the caret visible. Fires on focus gain, on
 *     setText, on selection change, on IME resize, and on every keystroke.
 *     This is the obvious one, and the cause of "it scrolls a line whenever
 *     the cursor moves".
 *
 *  2. TextView.moveCursorToVisibleOffset()
 *     The non-obvious one. When a finger drag-scroll ends,
 *     ArrowKeyMovementMethod.onTouchEvent compares scrollY at ACTION_DOWN
 *     against scrollY at ACTION_UP; if they differ, it calls this, which
 *     relocates the INSERTION POINT into the visible region via
 *     Selection.setSelection. Nothing scrolls -- the caret moves instead.
 *     That makes it invisible to any amount of scroll interception, and it
 *     silently changes where your next keystroke lands.
 *
 * Disable both, then implement the scroll policy we actually want.
 *
 * Usage: swap the tag on your existing <EditText>, keep every attribute and
 * the id. AppCompatEditText is an EditText subclass, so findViewById<EditText>,
 * TextWatchers, setText and view binding all keep working unchanged.
 */
class NoScrollEditText @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

	/**
	 * Clear this while loading text programmatically into a field that already
	 * holds focus, so the load is not mistaken for the user typing:
	 *
	 *     respondToTextChanges = false
	 *     setText(saved); setSelection(savedCursor)
	 *     post { respondToTextChanges = true }
	 */
	var respondToTextChanges = true

	// ---- 1. never scroll automatically ----------------------------------

	override fun bringPointIntoView(offset: Int): Boolean = false

	// ---- 2. never relocate the cursor automatically ---------------------

	// The caller ignores our return value and returns true regardless, which
	// has a useful side effect: ending a drag-scroll no longer re-places the
	// cursor at the lift point either. A plain tap involves no scroll, takes
	// a different branch, and still places the cursor normally.
	override fun moveCursorToVisibleOffset(): Boolean = false

	// ---- 3. the only thing permitted to scroll: typing ------------------

	private val caretCheck = Runnable { ensureCaretVisible() }

	override fun onTextChanged(
		text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int
	) {
		super.onTextChanged(text, start, lengthBefore, lengthAfter)

		// Also fires during construction, before layout exists. isFocused is
		// a cheap proxy for "the user is actually typing".
		if (!isFocused || !respondToTextChanges) return

		// `layout` still describes the pre-change text; wait one frame.
		removeCallbacks(caretCheck)
		post(caretCheck)
	}

	/**
	 * Hysteresis with edge alignment:
	 *
	 *   caret fully visible         -> do nothing
	 *   caret above the top edge    -> scroll so caret top == viewport top
	 *   caret below the bottom edge -> scroll so caret bottom == viewport bottom
	 *
	 * "Then wait until it leaves view again" needs no state of its own: once
	 * aligned to an edge the caret is visible, so the first branch holds until
	 * it exits once more.
	 */
	private fun ensureCaretVisible() {
		val l = layout ?: return
		val offset = selectionEnd
		if (offset < 0) return

		// Visible band, in layout coordinates. Extended padding is what
		// TextView itself uses here -- it accounts for the hint and drawables.
		val vspace = height - extendedPaddingTop - extendedPaddingBottom
		if (vspace <= 0) return

		val line = l.getLineForOffset(offset)
		val caretTop = l.getLineTop(line)
		val caretBottom = l.getLineBottom(line)

		val target = when {
			caretTop < scrollY -> caretTop
			caretBottom > scrollY + vspace -> caretBottom - vspace
			else -> return   // already visible: the common case, no work
		}

		// Re-clamp each time; layout height changes as text is added/removed.
		val max = (l.height - vspace).coerceAtLeast(0)

		// No X handling: textMultiLine wraps, so there is no horizontal
		// scroll to fight. scrollX is passed through untouched.
		scrollTo(scrollX, target.coerceIn(0, max))
	}
}