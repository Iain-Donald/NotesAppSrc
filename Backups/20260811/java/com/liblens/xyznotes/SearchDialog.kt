package com.liblens.xyznotes

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.liblens.xyznotes.databinding.DialogSearchBinding

object SearchDialog {

	/** @param scopeSectionId non-null enables the section/all scope toggle and
	 *  starts scoped to that section. Null means top-level: no scope row. */
	fun show(activity: Activity, appData: AppData, scopeSectionId: String? = null) {
		val b = DialogSearchBinding.inflate(activity.layoutInflater)

		val resultAdapter = SearchResultAdapter { result ->
			activity.startActivity(
				Intent(activity, NoteDetailActivity::class.java)
					.putExtra("noteId", result.note.id)
			)
		}
		b.rvSearchResults.layoutManager = LinearLayoutManager(activity)
		b.rvSearchResults.adapter = resultAdapter

		val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
		dialog.setContentView(b.root)
		dialog.setCanceledOnTouchOutside(true)
		dialog.window?.apply {
			setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
			setDimAmount(0.7f)
			addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
			setGravity(Gravity.TOP)
			setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
		}
		//b.root.setOnClickListener { dialog.dismiss() }
		//b.cardSearch.setOnClickListener { }

		// Push the card below the status bar and any display cutout.
		b.cardSearch.post {
			val insets = ViewCompat.getRootWindowInsets(b.cardSearch)
			val top = if (insets != null) maxOf(
				insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
				insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
			) else 0
			(b.cardSearch.layoutParams as FrameLayout.LayoutParams).let {
				it.topMargin = top + 8
				b.cardSearch.layoutParams = it
			}
		}

		val scoped = scopeSectionId != null
		var thisSectionOnly = scoped

		fun runSearch() {
			val query = b.etSearchQuery.text.toString()
			val results = SearchHelper.search(
				appData = appData,
				query = query,
				caseSensitive = b.btnCaseSensitive.isSelected,
				scopeSectionId = if (thisSectionOnly) scopeSectionId else null,
				includeContent = b.switchIncludeContent.isChecked
			)
			resultAdapter.submitList(results)
			b.tvNoResults.visibility =
				if (results.isEmpty() && query.isNotBlank()) View.VISIBLE else View.GONE
		}

		fun paintScope() {
			b.btnScopeSection.isSelected = thisSectionOnly
			b.btnScopeSection.alpha = if (thisSectionOnly) 1f else 0.5f
			b.btnScopeAll.isSelected = !thisSectionOnly
			b.btnScopeAll.alpha = if (thisSectionOnly) 0.5f else 1f
		}

		if (scoped) {
			b.btnScopeSection.setOnClickListener { thisSectionOnly = true; paintScope(); runSearch() }
			b.btnScopeAll.setOnClickListener { thisSectionOnly = false; paintScope(); runSearch() }
			paintScope()
		}

		var optionsVisible = false
		b.btnSearchOptions.setOnClickListener {
			optionsVisible = !optionsVisible
			val vis = if (optionsVisible) View.VISIBLE else View.GONE
			b.dividerOptions.visibility = vis
			b.layoutContentRow.visibility = vis
			// Scope row only exists when a section provides scope.
			b.layoutScopeRow.visibility = if (scoped) vis else View.GONE
		}

		b.btnCaseSensitive.setOnClickListener {
			b.btnCaseSensitive.isSelected = !b.btnCaseSensitive.isSelected
			b.btnCaseSensitive.alpha = if (b.btnCaseSensitive.isSelected) 1f else 0.5f
			runSearch()
		}
		b.btnCaseSensitive.alpha = 0.5f

		//b.switchIncludeContent.isChecked = true // set in XML instead, more reliable.
		b.switchIncludeContent.setOnCheckedChangeListener { _, _ -> runSearch() }

		b.etSearchQuery.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
			override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
			override fun afterTextChanged(s: Editable?) { runSearch() }
		})

		b.layoutScopeRow.visibility = View.GONE
		dialog.show()
	}
}