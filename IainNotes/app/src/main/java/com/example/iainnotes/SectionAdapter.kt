package com.example.iainnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iainnotes.databinding.ItemSectionBinding

//json
class SectionAdapter(
    private val onClick: (Section) -> Unit,
    private val onRename: (Section) -> Unit,
    private val onDelete: (Section) -> Unit
) : ListAdapter<Section, SectionAdapter.ViewHolder>(DiffCallback()) {

    inner class ViewHolder(val binding: ItemSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(
            ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val section = getItem(position)
        holder.binding.tvSectionName.text = section.name
        holder.binding.root.setOnClickListener { onClick(section) }
        holder.binding.btnDeleteSection.setOnClickListener { onDelete(section) }
        holder.binding.root.setOnLongClickListener {
            onRename(section)
            true
        }
        holder.binding.btnDeleteSection.setOnClickListener { onDelete(section) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Section>() {
        override fun areItemsTheSame(a: Section, b: Section) = a.id == b.id
        override fun areContentsTheSame(a: Section, b: Section) = a == b
    }
}