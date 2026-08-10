package com.liblens.xyznotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.liblens.xyznotes.databinding.ItemSectionBinding

//json
class SectionAdapter(
    private val onClick: (Section) -> Unit,
    private val onRename: (Section) -> Unit,
    private val onDelete: (Section) -> Unit,
    private val onPin: (Section) -> Unit
) : ListAdapter<SectionAdapter.Row, SectionAdapter.ViewHolder>(DiffCallback()) {

    data class Row(val section: Section, val colorId: Int)

    private var categories: List<Category> = emptyList()
    private var sections: List<Section> = emptyList()

    fun updateCategories(list: List<Category>) { categories = list; rebuild() }
    fun submitSections(list: List<Section>) { sections = list; rebuild() }

    private fun rebuild() {
        submitList(sections.map { s ->
            Row(s, categories.find { it.id == s.categoryId }?.colorId ?: Palette.NONE)
        })
    }

    class ViewHolder(val binding: ItemSectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val section = row.section
        val b = holder.binding

        b.tvSectionName.text = section.name
        b.root.setOnClickListener { onClick(section) }
        b.root.setOnLongClickListener { onRename(section); true }
        b.btnDeleteSection.setOnClickListener { onDelete(section) }

        if (row.colorId == Palette.NONE) {
            b.colorBar.visibility = View.GONE
        } else {
            b.colorBar.visibility = View.VISIBLE
            b.colorBar.setBackgroundColor(Palette.colorOf(row.colorId))
        }

        b.btnPin.bindPin(section.pinned)
        b.btnPin.setOnClickListener { onPin(section) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Row>() {
        override fun areItemsTheSame(a: Row, b: Row) = a.section.id == b.section.id
        override fun areContentsTheSame(a: Row, b: Row) = a == b
    }
}