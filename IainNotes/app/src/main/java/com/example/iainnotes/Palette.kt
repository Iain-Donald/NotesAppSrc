package com.example.iainnotes

object Palette {
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
}