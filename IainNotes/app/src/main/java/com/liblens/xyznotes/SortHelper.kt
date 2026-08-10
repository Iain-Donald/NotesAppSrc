package com.liblens.xyznotes

object SortHelper {

	/** Pinned items first, each group sorted independently. */
	fun <T : Sortable> sorted(items: List<T>, sortOrder: String, sortAsc: Boolean): List<T> =
		apply(items.filter { it.pinned }, sortOrder, sortAsc) +
				apply(items.filter { !it.pinned }, sortOrder, sortAsc)

	private fun <T : Sortable> apply(items: List<T>, sortOrder: String, sortAsc: Boolean): List<T> {
		val sorted = when (sortOrder) {
			"alpha" -> items.sortedBy { it.sortName.lowercase() }
			else -> items.sortedBy { it.sortCreatedAt }
		}
		return if (sortAsc) sorted else sorted.reversed()
	}

	fun sortedSections(s: List<Section>, order: String, asc: Boolean) = sorted(s, order, asc)
	fun sortedNotes(n: List<Note>, order: String, asc: Boolean) = sorted(n, order, asc)
}