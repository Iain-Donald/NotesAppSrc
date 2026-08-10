package com.liblens.xyznotes

object SearchHelper {

	/**
	 * Searches notes using the already-loaded AppData cache. No additional I/O is performed
	 * because note content is fully loaded into AppData.notes during DataStore.load().
	 *
	 * @param appData       The current cached app data.
	 * @param query         The search string. Empty query returns no results.
	 * @param caseSensitive Whether the match is case-sensitive.
	 * @param scopeSectionId If non-null, restricts results to notes in that section only.
	 * @param includeContent Whether to also search inside note content bodies.
	 */
	fun search(
		appData: AppData,
		query: String,
		caseSensitive: Boolean,
		scopeSectionId: String?,
		includeContent: Boolean
	): List<SearchResult> {
		if (query.isBlank()) return emptyList()

		val normalizedQuery = if (caseSensitive) query else query.lowercase()

		val sectionNameById = appData.sections.associate { it.id to it.name }

		val candidateNotes = if (scopeSectionId != null) {
			appData.notes.filter { it.sectionId == scopeSectionId }
		} else {
			appData.notes
		}

		return candidateNotes.mapNotNull { note ->
			val titleToMatch = if (caseSensitive) note.title else note.title.lowercase()
			val titleMatches = titleToMatch.contains(normalizedQuery)

			var contentMatches = false
			val snippet: String? = if (includeContent) {
				val contentToMatch = if (caseSensitive) note.content else note.content.lowercase()
				val idx = contentToMatch.indexOf(normalizedQuery)
				if (idx >= 0) {
					contentMatches = true
					extractSnippet(note.content, idx, query.length)
				} else if (titleMatches && note.content.isNotBlank()) {
					extractSnippet(note.content, 0, 0)
				} else null
			} else null

			if (!titleMatches && !contentMatches) return@mapNotNull null

			SearchResult(note, sectionNameById[note.sectionId] ?: "", snippet)
		}
	}

	private fun extractSnippet(content: String, matchIndex: Int, matchLength: Int): String {
		val contextRadius = 60
		val start = maxOf(0, matchIndex - contextRadius)
		val end = minOf(content.length, matchIndex + matchLength + contextRadius)
		val raw = content.substring(start, end).replace('\n', ' ')
		return if (start > 0) "…$raw" else raw
	}
}