package com.liblens.xyznotes

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

object ActivityBuilder {

	/** Inflates the shared themed input field, optionally prefilled. */
	fun input(activity: Activity, prefill: String? = null): EditText {
		val ctx = ContextThemeWrapper(activity, R.style.RoundedDialog)
		val view = activity.layoutInflater.cloneInContext(ctx)
			.inflate(R.layout.item_dialogalert, null) as EditText
		prefill?.let { view.setText(it) }
		return view
	}

	/** Themed builder — use in place of AlertDialog.Builder(this). */
	fun dialog(activity: Activity): AlertDialog.Builder =
		AlertDialog.Builder(activity, R.style.RoundedDialog)

	private fun dp(v: View, n: Int) = (n * v.resources.displayMetrics.density).toInt()

	private fun paintDot(view: View, colorId: Int, selected: Boolean = false) {
		val d = view.resources.displayMetrics.density
		view.background = GradientDrawable().apply {
			shape = GradientDrawable.OVAL
			setColor(if (colorId == Palette.NONE) Color.TRANSPARENT else Palette.colorOf(colorId))
			setStroke(((if (selected) 3 else 1) * d).toInt(), Palette.border)
		}
	}

	/** The 24-swatch grid, used by the new-category dialog. */
	private fun swatchGrid(ctx: Context, initial: Int, onPick: (Int) -> Unit): GridLayout {
		val d = ctx.resources.displayMetrics.density
		val size = (32 * d).toInt()
		val gap = (4 * d).toInt()
		var current = initial

		val grid = GridLayout(ctx).apply { columnCount = 5 }

		fun repaint() {
			for (i in 0 until grid.childCount) {
				val c = grid.getChildAt(i)
				paintDot(c, c.tag as Int, c.tag == current)
			}
		}

		fun add(id: Int) {
			grid.addView(View(ctx).apply {
				tag = id
				layoutParams = GridLayout.LayoutParams().also {
					it.width = size; it.height = size
					it.setMargins(gap, gap, gap, gap)
				}
				setOnClickListener {
					current = id
					onPick(id)
					repaint()
				}
			})
		}

		add(Palette.NONE)
		Palette.ids.forEach { add(it) }
		repaint()
		return grid
	}

	/** Create/edit dialog. `existing == null` means create. */
	fun categoryDialog(
		activity: Activity,
		existing: Category?,
		onSubmit: (String, Int) -> Unit,
		onDelete: (() -> Unit)? = null
	) {
		val ctx = ContextThemeWrapper(activity, R.style.RoundedDialog)
		val d = activity.resources.displayMetrics.density
		var colorId = existing?.colorId ?: Palette.NONE

		val name = EditText(ctx).apply {
			hint = "Group name"
			existing?.let { setText(it.name) }
		}

		val header = LinearLayout(ctx).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
			if (existing != null && onDelete != null) {
				addView(ImageButton(ctx).apply {
					setImageResource(android.R.drawable.ic_menu_delete)
					background = null
					imageTintList = Palette.tint(Palette.iconDim)
					layoutParams = LinearLayout.LayoutParams((40 * d).toInt(), (40 * d).toInt())
					setOnClickListener { confirmDeleteCategory(activity, existing, onDelete) }
				})
			}
		}

		val column = LinearLayout(ctx).apply {
			orientation = LinearLayout.VERTICAL
			setPadding((24 * d).toInt(), (8 * d).toInt(), (24 * d).toInt(), 0)
			addView(header)
			addView(swatchGrid(ctx, colorId) { colorId = it })
		}

		dialog(activity)
			.setTitle(if (existing == null) "New group" else "Edit group")
			.setView(column)
			.setPositiveButton(if (existing == null) "Create" else "Save") { _, _ ->
				val n = name.text.toString().trim()
				if (n.isNotEmpty()) onSubmit(n, colorId)
			}
			.setNegativeButton("Cancel", null)
			.show()
	}

	private fun confirmDeleteCategory(activity: Activity, cat: Category, onConfirm: () -> Unit) {
		dialog(activity)
			.setTitle("Delete \"${cat.name}\"?")
			.setMessage("Sections in this group will keep their names but lose the group.")
			.setPositiveButton("Delete") { _, _ -> onConfirm() }
			.setNegativeButton("Cancel", null)
			.show()
	}

	fun newCategoryDialog(activity: Activity, onSubmit: (String, Int) -> Unit) =
		categoryDialog(activity, null, onSubmit)

	/** Rename dialog view: name field + 24-swatch picker. Returns the root. */
	fun sectionEditor(
		activity: Activity,
		prefill: String?,
		selectedCategoryId: String,
		categories: List<Category>,
		onCreateCategory: ((Category) -> Unit) -> Unit,
		onEditCategory: (Category, (Category?) -> Unit) -> Unit
	): View {
		val ctx = ContextThemeWrapper(activity, R.style.RoundedDialog)
		val root = activity.layoutInflater.cloneInContext(ctx)
			.inflate(R.layout.item_dialog_section, null)

		root.findViewById<EditText>(R.id.sectionInput).apply { prefill?.let { setText(it) } }

		val dot = root.findViewById<View>(R.id.colorDot)
		val label = root.findViewById<TextView>(R.id.tvCategory)
		val row = root.findViewById<View>(R.id.categoryValue)
		val known = categories.toMutableList()

		fun select(cat: Category?) {
			root.setTag(R.id.colorDot, cat?.id ?: "")
			label.text = cat?.name ?: "Category"
			paintDot(dot, cat?.colorId ?: Palette.NONE)
		}

		select(categories.find { it.id == selectedCategoryId })

		row.setOnClickListener { anchor ->
			val column = LinearLayout(ctx).apply {
				orientation = LinearLayout.VERTICAL
				setPadding(dp(root, 8), dp(root, 8), dp(root, 8), dp(root, 8))
				background = GradientDrawable().apply {
					cornerRadius = 12 * ctx.resources.displayMetrics.density
					setColor(Palette.surface)
				}
			}
			val popup = PopupWindow(column, WRAP_CONTENT, WRAP_CONTENT, true)
			popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

			fun addRow(text: String, colorId: Int, onEdit: (() -> Unit)? = null, onTap: () -> Unit) {
				column.addView(LinearLayout(ctx).apply {
					orientation = LinearLayout.HORIZONTAL
					gravity = Gravity.CENTER_VERTICAL
					setPadding(dp(root, 8), dp(root, 10), dp(root, 8), dp(root, 10))
					addView(View(ctx).apply {
						layoutParams = LinearLayout.LayoutParams(dp(root, 20), dp(root, 20))
						paintDot(this, colorId)
					})
					addView(TextView(ctx).apply {
						this.text = text
						textSize = 16f
						setTextColor(Palette.textPrimary)
						setPadding(dp(root, 12), 0, dp(root, 12), 0)
						layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
						setOnClickListener { onTap(); popup.dismiss() }
					})
					if (onEdit != null) {
						addView(ImageButton(ctx).apply {
							setImageResource(android.R.drawable.ic_menu_edit)
							background = null
							imageTintList = Palette.tint(Palette.iconDim)
							layoutParams = LinearLayout.LayoutParams(dp(root, 32), dp(root, 32))
							setOnClickListener { popup.dismiss(); onEdit() }
						})
					} else {
						setOnClickListener { onTap(); popup.dismiss() }
					}
				})
			}
			addRow("None", Palette.NONE) { select(null) }
			known.forEachIndexed { index, c ->
				addRow(
					c.name,
					c.colorId,
					onEdit = {
						onEditCategory(c) { updated ->
							if (updated == null) known.removeAt(index) else known[index] = updated
							select(updated)
						}
					}
				) { select(c) }
			}
			addRow("New group…", Palette.NONE) {
				onCreateCategory { created ->
					known.add(created)
					select(created)
				}
			}
			popup.showAsDropDown(anchor, 0, dp(root, 4))
		}
		return root
	}

	fun selectedCategoryId(root: View): String = root.getTag(R.id.colorDot) as? String ?: ""
}

