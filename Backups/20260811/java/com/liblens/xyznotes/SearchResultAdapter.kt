package com.liblens.xyznotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liblens.xyznotes.databinding.ItemSearchResultBinding

data class SearchResult(
	val note: Note,
	val sectionName: String,
	val contentSnippet: String?
)

class SearchResultAdapter(
	private val onTap: (SearchResult) -> Unit
) : ListAdapter<SearchResult, SearchResultAdapter.ViewHolder>(DiffCallback()) {

	inner class ViewHolder(val binding: ItemSearchResultBinding) :
		RecyclerView.ViewHolder(binding.root)

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
		ViewHolder(
			ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
		)

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		val result = getItem(position)
		holder.binding.tvResultSection.text = "Sections > ${result.sectionName}"
		holder.binding.tvResultTitle.text = result.note.title
		if (result.contentSnippet != null) {
			holder.binding.tvResultSnippet.text = result.contentSnippet
			holder.binding.tvResultSnippet.visibility = View.VISIBLE
		} else {
			holder.binding.tvResultSnippet.visibility = View.GONE
		}
		holder.binding.root.setOnClickListener { onTap(result) }
	}

	class DiffCallback : DiffUtil.ItemCallback<SearchResult>() {
		override fun areItemsTheSame(a: SearchResult, b: SearchResult) =
			a.note.id == b.note.id
		override fun areContentsTheSame(a: SearchResult, b: SearchResult) = a == b
	}
}